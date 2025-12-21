#!/usr/bin/env python3
import json
import os
import sys
import time
from datetime import datetime

import requests


def now_ts() -> str:
    return datetime.utcnow().strftime("%Y%m%d_%H%M%S")


def load_token(path: str) -> str:
    with open(path, "r", encoding="utf-8") as handle:
        token = handle.read().strip()
    if not token:
        raise RuntimeError(f"Token file is empty: {path}")
    return token


def request_json(session, method, url, **kwargs):
    resp = session.request(method, url, timeout=30, **kwargs)
    payload = None
    if resp.content:
        content_type = resp.headers.get("content-type", "")
        if "application/json" in content_type:
            payload = resp.json()
        else:
            payload = resp.text
    return resp, payload


def record(results, name, ok, details):
    results.append(
        {
            "step": name,
            "ok": ok,
            "details": details,
        }
    )


def main() -> int:
    base_url = os.environ.get("API_BASE_URL", "http://localhost:7700").rstrip("/")
    admin_token_path = os.environ.get("ADMIN_TOKEN_PATH", "tmp/admin.access_token")
    viewer_token_path = os.environ.get("VIEWER_TOKEN_PATH", "tmp/viewer.access_token")
    output_dir = os.environ.get("OUTPUT_DIR", "tmp")
    cleanup = os.environ.get("CLEANUP", "0") == "1"

    os.makedirs(output_dir, exist_ok=True)

    admin_token = load_token(admin_token_path)
    viewer_token = load_token(viewer_token_path)

    admin = requests.Session()
    admin.headers.update(
        {
            "Authorization": f"Bearer {admin_token}",
            "Accept": "application/json",
        }
    )

    viewer = requests.Session()
    viewer.headers.update(
        {
            "Authorization": f"Bearer {viewer_token}",
            "Accept": "application/json",
        }
    )

    run_id = f"phase-c-{now_ts()}"
    results = []
    artifacts = {
        "run_id": run_id,
        "base_url": base_url,
        "created": {},
    }

    try:
        # 1) Root folders
        resp, roots = request_json(admin, "GET", f"{base_url}/api/v1/folders/roots")
        ok = resp.ok and isinstance(roots, list) and roots
        record(results, "list_root_folders", ok, {"status": resp.status_code, "roots": roots})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        root = next((r for r in roots if r.get("name") == "uploads"), roots[0])
        root_id = root["id"]
        artifacts["created"]["root_folder_id"] = root_id

        # 2) Create base folder
        base_folder_name = f"api-{run_id}"
        resp, base_folder = request_json(
            admin,
            "POST",
            f"{base_url}/api/v1/folders",
            json={
                "name": base_folder_name,
                "description": "Phase C security verification",
                "parentId": root_id,
            },
        )
        ok = resp.status_code == 201
        record(results, "create_base_folder", ok, {"status": resp.status_code, "folder": base_folder})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        base_folder_id = base_folder["id"]
        artifacts["created"]["base_folder_id"] = base_folder_id

        # 3) Upload document
        filename = f"{run_id}.txt"
        file_body = f"Phase C security verification {run_id}\n".encode("utf-8")
        resp, upload = request_json(
            admin,
            "POST",
            f"{base_url}/api/v1/documents/upload",
            files={"file": (filename, file_body, "text/plain")},
            data={"folderId": base_folder_id},
        )
        ok = resp.ok and isinstance(upload, dict) and upload.get("success")
        record(results, "upload_document", ok, {"status": resp.status_code, "response": upload})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        document_id = upload.get("documentId")
        artifacts["created"]["document_id"] = document_id

        # 4) Disable inheritance + grant READ to viewer
        resp, inherit = request_json(
            admin,
            "POST",
            f"{base_url}/api/v1/security/nodes/{base_folder_id}/inherit-permissions",
            params={"inherit": "false"},
        )
        ok = resp.ok
        record(results, "disable_inherit_permissions", ok, {"status": resp.status_code})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # Remove inherited public create permissions (EVERYONE) for the isolated test folder
        resp, remove_everyone = request_json(
            admin,
            "DELETE",
            f"{base_url}/api/v1/security/nodes/{base_folder_id}/permissions",
            params={
                "authority": "EVERYONE",
                "permissionType": "CREATE_CHILDREN",
            },
        )
        ok = resp.status_code in (200, 204)
        record(results, "remove_everyone_create_children", ok, {"status": resp.status_code, "response": remove_everyone})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        resp, grant = request_json(
            admin,
            "POST",
            f"{base_url}/api/v1/security/nodes/{base_folder_id}/permissions",
            params={
                "authority": "viewer",
                "authorityType": "USER",
                "permissionType": "READ",
                "allowed": "true",
            },
        )
        ok = resp.ok
        record(results, "grant_viewer_read", ok, {"status": resp.status_code})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 5) Viewer can read folder + contents
        resp, folder = request_json(viewer, "GET", f"{base_url}/api/v1/folders/{base_folder_id}")
        ok = resp.ok
        record(results, "viewer_get_folder", ok, {"status": resp.status_code, "folder": folder})

        resp, contents = request_json(
            viewer, "GET", f"{base_url}/api/v1/folders/{base_folder_id}/contents"
        )
        ok = resp.ok
        record(results, "viewer_get_contents", ok, {"status": resp.status_code})

        # 6) Viewer cannot create folder
        resp, create_denied = request_json(
            viewer,
            "POST",
            f"{base_url}/api/v1/folders",
            json={
                "name": f"{run_id}-viewer-denied",
                "parentId": base_folder_id,
            },
        )
        ok = resp.status_code in (401, 403)
        record(results, "viewer_create_folder_denied", ok, {"status": resp.status_code, "response": create_denied})

        # 7) Viewer cannot delete document
        resp, delete_denied = request_json(
            viewer,
            "DELETE",
            f"{base_url}/api/v1/nodes/{document_id}",
        )
        ok = resp.status_code in (401, 403)
        record(results, "viewer_delete_document_denied", ok, {"status": resp.status_code, "response": delete_denied})

        # 8) Create share link
        resp, share = request_json(
            admin,
            "POST",
            f"{base_url}/api/v1/share/nodes/{document_id}",
            json={
                "name": f"{run_id}-share",
                "permissionLevel": "VIEW",
                "maxAccessCount": 5,
            },
        )
        ok = resp.status_code == 201
        record(results, "create_share_link", ok, {"status": resp.status_code, "response": share})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        share_token = share.get("token")
        artifacts["created"]["share_token"] = share_token

        # 9) Public share access
        resp, access = request_json(
            requests.Session(),
            "GET",
            f"{base_url}/api/v1/share/access/{share_token}",
        )
        ok = resp.ok and isinstance(access, dict) and access.get("nodeId") == document_id
        record(results, "access_share_link", ok, {"status": resp.status_code, "response": access})

        # 10) Verify permissions endpoint for viewer
        resp, check = request_json(
            viewer,
            "GET",
            f"{base_url}/api/v1/security/nodes/{base_folder_id}/check-permission",
            params={"permissionType": "READ"},
        )
        ok = resp.ok and check is True
        record(results, "viewer_check_read_permission", ok, {"status": resp.status_code, "response": check})

        # 11) Optional cleanup
        if cleanup:
            resp, cleanup_res = request_json(
                admin,
                "DELETE",
                f"{base_url}/api/v1/folders/{base_folder_id}",
                params={"recursive": "true", "permanent": "true"},
            )
            record(results, "cleanup_base_folder", resp.status_code == 204, {"status": resp.status_code, "response": cleanup_res})

        return finalize(run_id, output_dir, results, artifacts, 0)
    except Exception as exc:
        record(results, "unhandled_exception", False, {"error": str(exc)})
        return finalize(run_id, output_dir, results, artifacts, 1)


def finalize(run_id, output_dir, results, artifacts, exit_code):
    output_path = os.path.join(output_dir, f"{run_id}.json")
    payload = {
        "run_id": run_id,
        "results": results,
        "artifacts": artifacts,
    }
    with open(output_path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
    print(f"Wrote {output_path}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())

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
    token_path = os.environ.get("TOKEN_PATH", "tmp/admin.access_token")
    output_dir = os.environ.get("OUTPUT_DIR", "tmp")
    cleanup = os.environ.get("CLEANUP", "0") == "1"

    os.makedirs(output_dir, exist_ok=True)

    token = load_token(token_path)
    session = requests.Session()
    session.headers.update(
        {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
        }
    )

    run_id = f"phase-b-{now_ts()}"
    results = []
    artifacts = {
        "run_id": run_id,
        "base_url": base_url,
        "created": {},
    }

    try:
        # 1) Root folders
        resp, roots = request_json(session, "GET", f"{base_url}/api/v1/folders/roots")
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
            session,
            "POST",
            f"{base_url}/api/v1/folders",
            json={
                "name": base_folder_name,
                "description": "Phase B API verification",
                "parentId": root_id,
            },
        )
        ok = resp.status_code == 201
        record(results, "create_base_folder", ok, {"status": resp.status_code, "folder": base_folder})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        base_folder_id = base_folder["id"]
        artifacts["created"]["base_folder_id"] = base_folder_id

        # 3) Create copy/move folders
        copy_folder_name = f"{run_id}-copy"
        resp, copy_folder = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/folders",
            json={
                "name": copy_folder_name,
                "description": "Phase B copy target",
                "parentId": base_folder_id,
            },
        )
        ok = resp.status_code == 201
        record(results, "create_copy_folder", ok, {"status": resp.status_code, "folder": copy_folder})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        copy_folder_id = copy_folder["id"]
        artifacts["created"]["copy_folder_id"] = copy_folder_id

        move_folder_name = f"{run_id}-move"
        resp, move_folder = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/folders",
            json={
                "name": move_folder_name,
                "description": "Phase B move target",
                "parentId": base_folder_id,
            },
        )
        ok = resp.status_code == 201
        record(results, "create_move_folder", ok, {"status": resp.status_code, "folder": move_folder})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        move_folder_id = move_folder["id"]
        artifacts["created"]["move_folder_id"] = move_folder_id

        # 4) Upload document
        filename = f"{run_id}.txt"
        file_body = f"Phase B API verification {run_id}\n".encode("utf-8")
        resp, upload = request_json(
            session,
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

        # 5) Copy document
        resp, copied = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/nodes/{document_id}/copy",
            params={
                "targetParentId": copy_folder_id,
                "newName": f"{run_id}-copy.txt",
            },
        )
        ok = resp.status_code in (200, 201)
        record(results, "copy_document", ok, {"status": resp.status_code, "response": copied})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        copied_id = copied.get("id")
        artifacts["created"]["copied_document_id"] = copied_id

        # 6) Move document
        resp, moved = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/nodes/{document_id}/move",
            params={"targetParentId": move_folder_id},
        )
        ok = resp.ok
        record(results, "move_document", ok, {"status": resp.status_code, "response": moved})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 7) Tag + assign
        tag_name = f"{run_id}-tag"
        resp, tag = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/tags",
            json={"name": tag_name, "description": "Phase B tag", "color": "#2196f3"},
        )
        ok = resp.status_code == 201
        record(results, "create_tag", ok, {"status": resp.status_code, "response": tag})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        tag_id = tag.get("id")
        artifacts["created"]["tag_id"] = tag_id

        resp, tag_assign = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/nodes/{document_id}/tags",
            json={"tagName": tag_name},
        )
        ok = resp.status_code == 204
        record(results, "assign_tag", ok, {"status": resp.status_code, "response": tag_assign})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 8) Category + assign
        category_name = f"{run_id}-category"
        resp, category = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/categories",
            json={"name": category_name, "description": "Phase B category"},
        )
        ok = resp.status_code == 201
        record(results, "create_category", ok, {"status": resp.status_code, "response": category})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)
        category_id = category.get("id")
        artifacts["created"]["category_id"] = category_id

        resp, category_assign = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/nodes/{document_id}/categories",
            json={"categoryId": category_id},
        )
        ok = resp.status_code == 204
        record(results, "assign_category", ok, {"status": resp.status_code, "response": category_assign})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 9) Checkout + checkin
        resp, checkout = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/documents/{document_id}/checkout",
        )
        ok = resp.ok
        record(results, "checkout_document", ok, {"status": resp.status_code, "response": checkout})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        new_body = f"Phase B checkin {run_id}\n".encode("utf-8")
        resp, checkin = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/documents/{document_id}/checkin",
            files={"file": (f"{run_id}-v2.txt", new_body, "text/plain")},
            data={"comment": "Phase B checkin", "majorVersion": "false"},
        )
        ok = resp.ok
        record(results, "checkin_document", ok, {"status": resp.status_code, "response": checkin})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 10) Version history
        resp, versions = request_json(
            session,
            "GET",
            f"{base_url}/api/v1/documents/{document_id}/versions",
        )
        ok = resp.ok and isinstance(versions, list) and len(versions) >= 2
        record(
            results,
            "list_versions",
            ok,
            {"status": resp.status_code, "count": len(versions) if isinstance(versions, list) else None},
        )

        # 11) Trash copy + restore
        resp, trash_move = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/trash/nodes/{copied_id}",
        )
        ok = resp.status_code == 204
        record(results, "trash_copy", ok, {"status": resp.status_code, "response": trash_move})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        resp, trash_items = request_json(session, "GET", f"{base_url}/api/v1/trash")
        found = False
        if isinstance(trash_items, list):
            found = any(item.get("id") == copied_id for item in trash_items)
        ok = resp.ok and found
        record(results, "verify_trash_contains_copy", ok, {"status": resp.status_code, "found": found})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        resp, trash_restore = request_json(
            session,
            "POST",
            f"{base_url}/api/v1/trash/{copied_id}/restore",
        )
        ok = resp.status_code == 204
        record(results, "restore_copy", ok, {"status": resp.status_code, "response": trash_restore})
        if not ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 12) Search
        search_ok = wait_for_search(session, base_url, document_id, filename, results)
        if not search_ok:
            # Attempt manual index + retry once
            resp, index_res = request_json(
                session,
                "POST",
                f"{base_url}/api/v1/search/index/{document_id}",
            )
            record(results, "manual_index_document", resp.ok, {"status": resp.status_code, "response": index_res})
            search_ok = wait_for_search(session, base_url, document_id, filename, results, label="search_document_after_manual_index")
        if not search_ok:
            return finalize(run_id, output_dir, results, artifacts, 1)

        # 13) Optional cleanup
        if cleanup:
            resp, cleanup_res = request_json(
                session,
                "DELETE",
                f"{base_url}/api/v1/folders/{base_folder_id}",
                params={"recursive": "true", "permanent": "true"},
            )
            record(results, "cleanup_base_folder", resp.status_code == 204, {"status": resp.status_code, "response": cleanup_res})

        return finalize(run_id, output_dir, results, artifacts, 0)
    except Exception as exc:
        record(results, "unhandled_exception", False, {"error": str(exc)})
        return finalize(run_id, output_dir, results, artifacts, 1)


def wait_for_search(session, base_url, document_id, filename, results, label="search_document"):
    for attempt in range(1, 8):
        resp, search = request_json(
            session,
            "GET",
            f"{base_url}/api/v1/search",
            params={"q": filename},
        )
        found = False
        total = None
        if isinstance(search, dict):
            content = search.get("content") or search.get("results")
            if isinstance(content, list):
                total = len(content)
                for item in content:
                    if item.get("id") == str(document_id) or item.get("name") == filename:
                        found = True
                        break
        if found:
            record(results, label, True, {"status": resp.status_code, "attempt": attempt, "total": total})
            return True
        time.sleep(2)

    record(results, label, False, {"status": resp.status_code, "attempts": 7})
    return False


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

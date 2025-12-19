# Encryption at Rest Configuration

This document describes the encryption options available for Athena ECM content storage.

## Overview

Athena ECM supports multiple approaches to data-at-rest encryption, ranging from infrastructure-level (low effort) to application-level (high security).

## Phase 1: Infrastructure-Level Encryption (Recommended Starting Point)

### Option A: MinIO Server-Side Encryption (SSE)

MinIO supports server-side encryption with multiple key management options.

#### SSE-S3 (MinIO-Managed Keys)

Add to `docker-compose.yml` minio environment:

```yaml
minio:
  environment:
    - MINIO_KMS_AUTO_ENCRYPTION=on
    - MINIO_KMS_SECRET_KEY=my-minio-key:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
```

All objects will be automatically encrypted at rest.

#### SSE-KMS (External Key Management)

For production environments, integrate with HashiCorp Vault or AWS KMS:

```yaml
minio:
  environment:
    - MINIO_KMS_KES_ENDPOINT=https://kes-server:7373
    - MINIO_KMS_KES_KEY_FILE=/path/to/kes-key.crt
    - MINIO_KMS_KES_CERT_FILE=/path/to/kes-cert.crt
    - MINIO_KMS_KES_KEY_NAME=my-encryption-key
```

### Option B: Host/Volume-Level Encryption

For filesystem storage (`ecm.storage.type=filesystem`), enable disk encryption:

#### Linux (LUKS)

```bash
# Create encrypted volume
cryptsetup luksFormat /dev/sdX
cryptsetup open /dev/sdX ecm_encrypted
mkfs.ext4 /dev/mapper/ecm_encrypted

# Mount for ECM content
mount /dev/mapper/ecm_encrypted /var/ecm/content
```

#### Docker Volume with Encrypted Backend

Use Docker with an encrypted storage driver or mount an encrypted filesystem as a volume.

### Option C: Cloud Provider Encryption

When deploying to cloud:

- **AWS EBS**: Enable encryption for the volume backing Docker/MinIO
- **Azure Disk**: Enable Azure Disk Encryption
- **GCP Persistent Disk**: All disks are encrypted by default

## Phase 2: Application-Level Encryption (Future)

For maximum security with client-controlled keys, application-level encryption can be implemented.

### Envelope Encryption Design

```
┌─────────────────────────────────────────┐
│          Key Management Service          │
│  (HashiCorp Vault / AWS KMS / Azure KV)  │
└─────────────────────────────────────────┘
                     │
                     │ Data Encryption Key (DEK)
                     ▼
┌─────────────────────────────────────────┐
│           ECM Core Application           │
│                                         │
│  Content → AES-256-GCM Encrypt → Store  │
│                                         │
│  Stored DEK (encrypted by KEK)          │
└─────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────┐
│         MinIO / Filesystem              │
│      (Encrypted content blobs)          │
└─────────────────────────────────────────┘
```

### Implementation Considerations

1. **Key Rotation**: Each document version can use a unique DEK
2. **Performance**: AES-256-GCM hardware acceleration available on modern CPUs
3. **Key Storage**: DEKs stored encrypted alongside content metadata
4. **Search**: Full-text search requires decryption or tokenized indexing

### Proposed Configuration (Future)

```yaml
ecm:
  encryption:
    enabled: true
    algorithm: AES-256-GCM
    kms:
      provider: vault  # vault, aws-kms, azure-keyvault
      endpoint: https://vault.example.com:8200
      key-name: ecm-master-key
      auth:
        method: kubernetes  # kubernetes, token, approle
```

## Current Recommendation

For most deployments, **Phase 1 Option A (MinIO SSE)** provides a good balance of security and simplicity:

1. Transparent encryption - no application changes needed
2. Minimal performance impact
3. Easy to enable/disable
4. Compatible with MinIO's existing backup/replication features

## Security Considerations

- Always use TLS for data in transit (configured in nginx/reverse proxy)
- Backup encryption keys separately from encrypted data
- Document key recovery procedures
- Consider compliance requirements (GDPR, HIPAA, etc.)
- Audit access to encryption keys

## Related Documentation

- [MinIO Server-Side Encryption](https://min.io/docs/minio/linux/operations/server-side-encryption.html)
- [HashiCorp Vault Integration](https://www.vaultproject.io/)
- [AWS KMS](https://aws.amazon.com/kms/)

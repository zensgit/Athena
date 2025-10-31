# Antivirus Integration

## Motivation
Scan uploads to prevent malware.

## Design
- Use ClamAV (clamd) via TCP; queue large files.
- Feature flag `antivirus.enabled` with async quarantine.

## Flow
Upload -> temp storage -> scan -> accept/quarantine -> index.

## Risks
Throughput impact; configure timeouts and DLQ.

## Test Plan
EICAR samples; stress test large batches.


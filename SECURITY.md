# Security Policy

## Supported Versions

The following versions of Scan are currently supported with security, privacy, and stability updates.

| Version       | Supported          |
| ------------- | ------------------ |
| >= 0.0.1-beta | :white_check_mark: |
| < 0.0.1-beta  | :x:                |



## Reporting a Vulnerability

If you discover a security vulnerability, privacy issue, unexpected behavior with permissions, or any issue that could negatively affect users or their data safety, please report it responsibly.

### Before Reporting
Please make sure that:
- The issue is reproducible
- You are using the latest supported version of Scan
- The issue is not caused by third-party modifications, custom ROMs, or unsupported Android environments

### How to Report
You can report vulnerabilities through:
- GitHub Issues (for non-sensitive reports and general bug reports)
- E-mail: hamzabellouchcontact@gmail.com / [Forms](https://forms.gle/2RZpv2rvur9BM4Vt6) 

When reporting, please include:
- Device model and Android OS version
- Scan application version
- Granted permissions state (e.g., Camera, Storage/Media, Notifications)
- Steps to reproduce the issue
- Screenshots or system logcat outputs if available
- A clear explanation of the potential security or privacy impact

### Response Policy
Security reports are reviewed as quickly as possible.  
If the issue is confirmed:
- The vulnerability will be investigated, verified, and patched
- A fix will be included in the next update release
- Credit may be given to the reporter if requested

If the report is invalid, incomplete, or not reproducible, it may be closed without action.



## Security & Architecture Notes

Scan is designed with a strict **Privacy-First & On-Device Processing Model**:

- **Local Execution:** Document scanning, edge detection, perspective cropping, image enhancement filters, and PDF/Word/TXT conversions operate 100% locally on your device. No camera frames, scanned documents, or converted files are transmitted to external servers.
- **Root-Free Operation:** Scan does not require root privileges. It relies entirely on official Android system APIs (`CameraX`, `PdfRenderer`, `MediaStore`, `OkHttp`).
- **Camera Isolation:** The camera hardware is accessed exclusively when the user is actively on the Document Scanner screen. No background recording or silent frame capture takes place at any time.
- **Transient Memory Handling:** Temporary camera buffers and processing bitmaps are cleared promptly when leaving the scanner or completing export, keeping the memory footprint minimal and secure.

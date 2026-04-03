#!/usr/bin/env python3
"""
Generate a signed RS256 JWT for the products API.

Usage:
  gen-jwt <roles>              # e.g. gen-jwt ADMIN,READ
  gen-jwt <roles> <sub>        # e.g. gen-jwt ADMIN,READ my-user
"""

import sys
import time
import jwt
from pathlib import Path
from cryptography.hazmat.primitives.serialization import load_pem_private_key

PRIVATE_KEY_PATH = Path(__file__).parent.parent / "src/main/resources/certs/private.pem"
EXPIRY_SECONDS = 86400  # 24 hours


def main():
    if len(sys.argv) < 2:
        print("Usage: gen-jwt <roles> [sub]", file=sys.stderr)
        print("  roles: comma-separated, e.g. ADMIN,READ", file=sys.stderr)
        sys.exit(1)

    roles = [r.strip() for r in sys.argv[1].split(",") if r.strip()]
    sub = sys.argv[2] if len(sys.argv) >= 3 else "api-user"

    if not PRIVATE_KEY_PATH.exists():
        print(f"Error: private key not found at {PRIVATE_KEY_PATH}", file=sys.stderr)
        sys.exit(1)

    key = load_pem_private_key(PRIVATE_KEY_PATH.read_bytes(), password=None)

    now = int(time.time())
    payload = {
        "sub": sub,
        "iss": "products-app",
        "iat": now,
        "exp": now + EXPIRY_SECONDS,
        "roles": roles,
    }

    token = jwt.encode(payload, key, algorithm="RS256")
    print(token)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Generate a Firebase ID token (JWT) using a service account JSON.

The script:
  1. Creates a Firebase custom token signed with the service account private key
  2. Exchanges it for a Firebase ID token via the Firebase Auth REST API
     (the ID token is what your Spring backend validates)

Usage:
  python scripts/gen_firebase_jwt.py [--sa PATH] [--uid UID] [--roles ROLE1,ROLE2]

  --sa      Path to service account JSON (default: src/main/resources/google/jardindecasablanca-firebase-adminsdk-fbsvc-45ef4bef91.json)
  --uid     Firebase UID to impersonate (default: dev-user)
  --roles   Comma-separated roles to embed in custom claims (default: ADMIN)

Requirements:
  pip install PyJWT cryptography requests

The Firebase Auth REST API requires the project's Web API Key.
It is read from the service account JSON's `project_id` field and looked up
via the Firebase Management API, OR you can pass it directly with --api-key.

Simplest approach: pass --api-key explicitly (find it in Firebase Console →
Project Settings → General → Web API Key).

  python scripts/gen_firebase_jwt.py --api-key YOUR_WEB_API_KEY
"""

import argparse
import json
import time
import sys
from pathlib import Path

try:
    import jwt
    import requests
    from cryptography.hazmat.primitives.serialization import load_pem_private_key
except ImportError:
    print("Missing dependencies. Run: pip install PyJWT cryptography requests", file=sys.stderr)
    sys.exit(1)

DEFAULT_SA = Path(__file__).parent.parent / "src/main/resources/google/jardindecasablanca-firebase-adminsdk-fbsvc-45ef4bef91.json"
CUSTOM_TOKEN_AUDIENCE = "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"
EXCHANGE_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken"


def load_service_account(path: Path) -> dict:
    with open(path) as f:
        return json.load(f)


def build_custom_token(sa: dict, uid: str, claims: dict) -> str:
    """Mint a Firebase custom token signed with the service account private key."""
    private_key = load_pem_private_key(sa["private_key"].encode(), password=None)
    now = int(time.time())
    payload = {
        "iss": sa["client_email"],
        "sub": sa["client_email"],
        "aud": CUSTOM_TOKEN_AUDIENCE,
        "iat": now,
        "exp": now + 3600,
        "uid": uid,
        "claims": claims,
    }
    return jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": sa["private_key_id"]})


def exchange_for_id_token(custom_token: str, api_key: str) -> str:
    """Exchange a custom token for a Firebase ID token via REST API."""
    resp = requests.post(
        f"{EXCHANGE_URL}?key={api_key}",
        json={"token": custom_token, "returnSecureToken": True},
        timeout=10,
    )
    if not resp.ok:
        print(f"Error from Firebase Auth API: {resp.status_code} {resp.text}", file=sys.stderr)
        sys.exit(1)
    return resp.json()["idToken"]


def main():
    parser = argparse.ArgumentParser(description="Generate a Firebase ID token for local dev/testing")
    parser.add_argument("--sa", default=str(DEFAULT_SA), help="Path to service account JSON")
    parser.add_argument("--uid", default="dev-user", help="Firebase UID to impersonate")
    parser.add_argument("--roles", default="ADMIN", help="Comma-separated roles (e.g. ADMIN,USER)")
    parser.add_argument("--api-key", required=True, help="Firebase Web API Key (from Firebase Console → Project Settings → General)")
    args = parser.parse_args()

    sa_path = Path(args.sa)
    if not sa_path.exists():
        print(f"Service account file not found: {sa_path}", file=sys.stderr)
        sys.exit(1)

    sa = load_service_account(sa_path)
    roles = [r.strip() for r in args.roles.split(",") if r.strip()]
    claims = {"roles": roles}

    print(f"Project:  {sa['project_id']}", file=sys.stderr)
    print(f"UID:      {args.uid}", file=sys.stderr)
    print(f"Roles:    {roles}", file=sys.stderr)
    print("", file=sys.stderr)

    custom_token = build_custom_token(sa, args.uid, claims)
    id_token = exchange_for_id_token(custom_token, args.api_key)

    print(id_token)


if __name__ == "__main__":
    main()

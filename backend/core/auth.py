import hmac
import hashlib
import base64
import json
import time
from datetime import datetime, timedelta
from typing import Optional, Dict, Any
from fastapi import Depends, HTTPException, status, Request
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy import func

from database.session import get_db
from database.models import User
from backend.core.config import settings
from backend.core.cache import auth_rate_limiter

security = HTTPBearer(auto_error=False)

JWT_SECRET = getattr(settings, "JWT_SECRET", "gold-ai-analyst-super-secret-key-38491")
JWT_ALGORITHM = "HS256"
JWT_EXPIRATION_HOURS = 24

def hash_password(password: str) -> str:
    salt = "gold_salt_77"
    return hashlib.sha256(f"{salt}{password}".encode("utf-8")).hexdigest()

def verify_password(plain_password: str, hashed_password: str) -> bool:
    return hmac.compare_digest(hash_password(plain_password), hashed_password)

def create_access_token(data: Dict[str, Any], expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    expire = time.time() + (expires_delta.total_seconds() if expires_delta else JWT_EXPIRATION_HOURS * 3600)
    to_encode.update({"exp": expire})
    
    header = {"alg": JWT_ALGORITHM, "typ": "JWT"}
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(to_encode).encode()).decode().rstrip("=")
    
    signature = hmac.new(
        JWT_SECRET.encode(),
        f"{header_b64}.{payload_b64}".encode(),
        hashlib.sha256
    ).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    
    return f"{header_b64}.{payload_b64}.{sig_b64}"

def decode_access_token(token: str) -> Optional[Dict[str, Any]]:
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header_b64, payload_b64, sig_b64 = parts
        
        # Verify signature
        expected_sig = hmac.new(
            JWT_SECRET.encode(),
            f"{header_b64}.{payload_b64}".encode(),
            hashlib.sha256
        ).digest()
        actual_sig = base64.urlsafe_b64decode(sig_b64 + "=" * (-len(sig_b64) % 4))
        if not hmac.compare_digest(expected_sig, actual_sig):
            return None
            
        # Decode payload
        payload_json = base64.urlsafe_b64decode(payload_b64 + "=" * (-len(payload_b64) % 4)).decode()
        payload = json.loads(payload_json)
        
        # Check expiry
        if payload.get("exp") and time.time() > payload["exp"]:
            return None
        return payload
    except Exception:
        return None

async def register_user(email: str, password: str, full_name: Optional[str], db: AsyncSession) -> User:
    """
    First-user super admin onboarding:
    The first registered user is automatically granted 'SUPER_ADMIN' role.
    Subsequent signups are assigned standard 'VIEWER' role.
    """
    res = await db.execute(select(func.count(User.id)))
    user_count = res.scalar() or 0
    
    is_first_user = (user_count == 0)
    assigned_role = "SUPER_ADMIN" if is_first_user else "VIEWER"
    
    user = User(
        email=email.strip().lower(),
        hashed_password=hash_password(password),
        full_name=full_name,
        role=assigned_role,
        is_superuser=is_first_user,
        is_active=True
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return user

async def get_current_user(
    auth: Optional[HTTPAuthorizationCredentials] = Depends(security),
    db: AsyncSession = Depends(get_db)
) -> User:
    if not auth or not auth.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing authentication credentials"
        )
    payload = decode_access_token(auth.credentials)
    if not payload or not payload.get("sub"):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired authentication token"
        )
    
    email = payload["sub"]
    res = await db.execute(select(User).where(User.email == email))
    user = res.scalars().first()
    if not user or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found or account deactivated"
        )
    return user

async def require_super_admin(current_user: User = Depends(get_current_user)) -> User:
    if current_user.role != "SUPER_ADMIN" and not current_user.is_superuser:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: Requires SUPER_ADMIN privileges"
        )
    return current_user

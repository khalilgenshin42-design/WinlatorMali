"""Android-only AES-GCM compatibility shim for Legendary.

The native _epiccrypto module delegates AES-GCM to Android's JCA provider.
This intentionally implements only the decrypt-and-verify API Legendary uses.
"""
from _epiccrypto import decrypt_aes_gcm

MODE_GCM = 11


class _GCMCipher:
    def __init__(self, key: bytes, nonce: bytes):
        self._key = bytes(key)
        self._nonce = bytes(nonce)

    def decrypt_and_verify(self, data: bytes, tag: bytes) -> bytes:
        return decrypt_aes_gcm(self._key, self._nonce, bytes(data), bytes(tag))


def new(key: bytes, mode: int, nonce: bytes):
    if mode != MODE_GCM:
        raise ValueError("Android Epic crypto shim supports AES-GCM only")
    if len(nonce) != 12:
        raise ValueError("AES-GCM nonce must be 12 bytes")
    if len(key) not in (16, 24, 32):
        raise ValueError("AES key must be 16, 24, or 32 bytes")
    return _GCMCipher(key, nonce)

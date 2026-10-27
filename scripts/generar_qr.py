#!/usr/bin/env python3
"""Genera qr.png para la URL pública.  Uso: pip install qrcode[pil]; python3 scripts/generar_qr.py https://makerlab-eia.onrender.com"""
import sys
import qrcode
if len(sys.argv) != 2:
    sys.exit(__doc__)
qrcode.make(sys.argv[1]).save("qr.png")
print("qr.png listo")

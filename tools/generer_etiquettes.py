"""Génère les étiquettes QR de démonstration (maquette de parking + voitures miniatures).

Formats des QR lus par l'application :
  - place    : PL:<zone>-<allée>-<place>   ex. PL:B-3-18
  - véhicule : VH:<VIN>                    ex. VH:VF3LCYHZPJS218456

Usage : python generer_etiquettes.py [sortie.pdf]      (nécessite reportlab)
"""
import sys

from reportlab.graphics import renderPDF
from reportlab.graphics.barcode.qr import QrCodeWidget
from reportlab.graphics.shapes import Drawing
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

INK = HexColor("#0F1D2B")
ORANGE = HexColor("#D9601A")
MUTED = HexColor("#56606B")

VEHICULES = [
    ("VF3LCYHZPJS218456", "Berline compacte · gris", "B-3-18"),
    ("VF1RJA00368512947", "SUV · blanc", "B-3-7"),
    ("KMHD841CBMU530927", "Berline · bleu", "B-4-21"),
    ("SJNFAAJ11U2640092", "SUV · gris", "B-2-15"),
    ("JTMBFREV30D118870", "SUV · noir", "B-3-2"),
    ("ZFA31200000904311", "Citadine · blanc", "B-1-12"),
]


def qr(c, data, x, y, size):
    w = QrCodeWidget(data)
    x0, y0, x1, y1 = w.getBounds()
    d = Drawing(size, size, transform=[size / (x1 - x0), 0, 0, size / (y1 - y0), 0, 0])
    d.add(w)
    renderPDF.draw(d, c, x, y)


def page_places(c, zone="B", allee=3):
    width, height = A4
    c.setFillColor(INK)
    c.setFont("Helvetica-Bold", 18)
    c.drawString(15 * mm, height - 20 * mm, f"QR des places · zone {zone}, allée {allee}")
    c.setFont("Helvetica", 10)
    c.setFillColor(MUTED)
    c.drawString(15 * mm, height - 27 * mm, "À coller devant chaque place de la maquette. Données fictives.")
    cols, size, gap = 6, 26 * mm, 5 * mm
    for i in range(24):
        col, row = i % cols, i // cols
        x = 15 * mm + col * (size + gap)
        y = height - 45 * mm - (row + 1) * (size + 14 * mm)
        code = f"{zone}-{allee}-{i + 1}"
        qr(c, f"PL:{code}", x, y + 8 * mm, size)
        c.setFillColor(ORANGE if code == "B-3-18" else INK)
        c.setFont("Helvetica-Bold", 11)
        c.drawCentredString(x + size / 2, y + 3 * mm, code.replace("-", " · "))
    c.showPage()


def page_vehicules(c):
    width, height = A4
    c.setFillColor(INK)
    c.setFont("Helvetica-Bold", 18)
    c.drawString(15 * mm, height - 20 * mm, "Étiquettes véhicules (QR + code court)")
    lw, lh = 88 * mm, 50 * mm
    for i, (vin, model, place) in enumerate(VEHICULES):
        col, row = i % 2, i // 2
        x = 15 * mm + col * (lw + 6 * mm)
        y = height - 32 * mm - (row + 1) * (lh + 8 * mm)
        c.setStrokeColor(HexColor("#C9C2B2"))
        c.roundRect(x, y, lw, lh, 4 * mm)
        qr(c, f"VH:{vin}", x + 4 * mm, y + 5 * mm, 40 * mm)
        c.setFillColor(INK)
        c.setFont("Helvetica-Bold", 9)
        c.drawString(x + 47 * mm, y + lh - 10 * mm, "Dakar AutoTrack")
        c.setFont("Courier-Bold", 8.5)
        c.drawString(x + 47 * mm, y + lh - 17 * mm, vin)
        c.setFont("Helvetica", 8)
        c.setFillColor(MUTED)
        c.drawString(x + 47 * mm, y + lh - 23 * mm, model)
        c.setFillColor(INK)
        c.setFont("Helvetica-Bold", 15)
        c.drawString(x + 47 * mm, y + 14 * mm, "DAT-" + vin[-6:])
        c.setFont("Helvetica", 8)
        c.setFillColor(MUTED)
        c.drawString(x + 47 * mm, y + 8 * mm, "Place de démo : " + place.replace("-", " · "))
    c.showPage()


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "etiquettes-demo.pdf"
    cv = canvas.Canvas(out, pagesize=A4)
    cv.setTitle("Dakar AutoTrack - étiquettes de démonstration")
    page_places(cv)
    page_vehicules(cv)
    cv.save()
    print("Étiquettes générées :", out)

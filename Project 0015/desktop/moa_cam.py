import sys, time, threading
from pathlib import Path
import cv2
import numpy as np

try:
    import pyvirtualcam
except Exception:
    pyvirtualcam = None

from PySide6.QtCore import Qt, QTimer, Signal, QObject
from PySide6.QtGui import QImage, QPixmap, QIcon
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QLabel, QPushButton, QLineEdit,
    QComboBox, QSlider, QVBoxLayout, QHBoxLayout, QGridLayout, QFrame,
    QStackedWidget, QMessageBox, QFileDialog
)

ROOT = Path(__file__).resolve().parents[1]
LOGO = ROOT / "moa_logo.png"
COPPER = "#D39A7B"
BG = "#071014"
PANEL = "#0B181D"
PANEL2 = "#102128"
TEXT = "#F3F5F5"
MUTED = "#8FA0A5"
TEAL = "#245968"

STYLE = f"""
QMainWindow, QWidget {{ background:{BG}; color:{TEXT}; font-family:'Segoe UI'; }}
QFrame#panel {{ background:{PANEL}; border:1px solid #1B333A; border-radius:14px; }}
QFrame#card {{ background:{PANEL2}; border:1px solid #1D3A42; border-radius:12px; }}
QLabel#title {{ font-size:26px; font-weight:700; color:{TEXT}; }}
QLabel#brand {{ color:{COPPER}; font-weight:700; letter-spacing:1px; }}
QLabel#section {{ color:{COPPER}; font-size:11px; font-weight:700; letter-spacing:1px; }}
QLabel#metric {{ font-size:20px; font-weight:700; }}
QLabel#muted {{ color:{MUTED}; }}
QPushButton {{ background:#132A31; border:1px solid #24454E; border-radius:8px; padding:9px 12px; color:{TEXT}; }}
QPushButton:hover {{ border-color:{COPPER}; }}
QPushButton#primary {{ background:{COPPER}; color:#111; font-weight:700; border:0; }}
QPushButton#danger {{ background:#5D2628; border-color:#8A3D40; }}
QComboBox, QLineEdit {{ background:#081419; border:1px solid #27434B; border-radius:7px; padding:8px; color:{TEXT}; }}
QSlider::groove:horizontal {{ height:4px; background:#203A42; }}
QSlider::handle:horizontal {{ width:14px; margin:-5px 0; background:{COPPER}; border-radius:7px; }}
QLabel#nav {{ padding:11px; color:{MUTED}; border-radius:8px; }}
QLabel#nav:hover {{ background:#132A31; color:{TEXT}; }}
"""

class Receiver(QObject):
    frame_ready = Signal(object)
    state = Signal(str)
    def __init__(self):
        super().__init__(); self.cap=None; self.running=False; self.thread=None
    def connect(self, url):
        self.disconnect(); self.running=True
        def run():
            self.cap=cv2.VideoCapture(url)
            if not self.cap.isOpened(): self.state.emit('Connection failed'); self.running=False; return
            self.state.emit('Connected • live')
            while self.running:
                ok, frame=self.cap.read()
                if not ok: time.sleep(.05); continue
                self.frame_ready.emit(frame)
            if self.cap: self.cap.release()
        self.thread=threading.Thread(target=run,daemon=True); self.thread.start()
    def disconnect(self):
        self.running=False
        if self.cap:
            try:self.cap.release()
            except:pass
            self.cap=None

class MoACam(QMainWindow):
    def __init__(self):
        super().__init__(); self.setWindowTitle('MoA Cam — Masters Of All'); self.resize(1440,900)
        if LOGO.exists(): self.setWindowIcon(QIcon(str(LOGO)))
        self.receiver=Receiver(); self.receiver.frame_ready.connect(self.on_frame); self.receiver.state.connect(self.status)
        self.last=None; self.processed=None; self.virtual=None; self.record=None; self.recording=False
        self.build_ui()
    def build_ui(self):
        root=QWidget(); self.setCentralWidget(root); main=QHBoxLayout(root); main.setContentsMargins(16,16,16,16); main.setSpacing(14)
        side=QFrame(); side.setObjectName('panel'); side.setFixedWidth(230); sv=QVBoxLayout(side); sv.setContentsMargins(18,18,18,18)
        logo=QLabel(); logo.setPixmap(QPixmap(str(LOGO)).scaled(72,72,Qt.KeepAspectRatio,Qt.SmoothTransformation)); logo.setAlignment(Qt.AlignCenter); sv.addWidget(logo)
        brand=QLabel('MoA Cam'); brand.setObjectName('title'); brand.setAlignment(Qt.AlignCenter); sv.addWidget(brand)
        by=QLabel('MASTERS OF ALL'); by.setObjectName('brand'); by.setAlignment(Qt.AlignCenter); sv.addWidget(by); sv.addSpacing(16)
        self.pages=QStackedWidget(); names=['Camera','Audio','Processing','Scopes','Record','Settings']
        for i,n in enumerate(names):
            b=QPushButton(n); b.setObjectName('nav'); b.clicked.connect(lambda _,x=i:self.pages.setCurrentIndex(x)); sv.addWidget(b)
        sv.addStretch(); sv.addWidget(QLabel('Your phone.\nYour sensor.\nYour production machine.'))
        main.addWidget(side)
        center=QVBoxLayout(); top=QHBoxLayout(); t=QLabel('MoA Cam'); t.setObjectName('title'); top.addWidget(t); top.addStretch()
        self.state=QLabel('● DISCONNECTED'); self.state.setObjectName('brand'); top.addWidget(self.state); center.addLayout(top)
        self.preview=QLabel('CONNECT YOUR PHONE\n\nOpen MoA Cam on Android and enter its IP below.'); self.preview.setAlignment(Qt.AlignCenter); self.preview.setMinimumSize(720,500); self.preview.setStyleSheet('background:#020708;border:1px solid #1B333A;border-radius:12px;color:#6F858B;font-size:18px;'); center.addWidget(self.preview,1)
        bar=QHBoxLayout(); self.url=QLineEdit('http://192.168.1.100:8080/stream'); self.url.setPlaceholderText('Phone stream URL'); bar.addWidget(self.url,1)
        c=QPushButton('CONNECT'); c.setObjectName('primary'); c.clicked.connect(lambda:self.receiver.connect(self.url.text().strip())); bar.addWidget(c)
        d=QPushButton('DISCONNECT'); d.clicked.connect(self.receiver.disconnect); bar.addWidget(d); center.addLayout(bar)
        self.pages.addWidget(self.camera_page()); self.pages.addWidget(self.audio_page()); self.pages.addWidget(self.processing_page()); self.pages.addWidget(self.scopes_page()); self.pages.addWidget(self.record_page()); self.pages.addWidget(self.settings_page())
        main.addLayout(center,1); main.addWidget(self.pages,0)
    def card(self,title,widget):
        f=QFrame(); f.setObjectName('card'); l=QVBoxLayout(f); h=QLabel(title.upper()); h.setObjectName('section'); l.addWidget(h); l.addWidget(widget); return f
    def camera_page(self):
        w=QWidget(); l=QVBoxLayout(w)
        self.iso=QComboBox(); self.iso.addItems(['AUTO','100','200','400','800','1600','3200','6400'])
        self.shutter=QComboBox(); self.shutter.addItems(['AUTO','1/24','1/30','1/48','1/50','1/60','1/120','1/240'])
        self.fps=QComboBox(); self.fps.addItems(['24','25','30','50','60'])
        self.open_gate=QComboBox(); self.open_gate.addItems(['Sensor / source','16:9','4:3','1:1','Custom crop'])
        self.zoom=QSlider(Qt.Horizontal); self.zoom.setRange(100,400); self.zoom.setValue(100)
        l.addWidget(self.card('Exposure',self.iso)); l.addWidget(self.card('Shutter',self.shutter)); l.addWidget(self.card('Output FPS',self.fps)); l.addWidget(self.card('Sensor / crop',self.open_gate)); l.addWidget(self.card('Digital zoom',self.zoom)); l.addStretch(); return w
    def audio_page(self):
        w=QWidget(); l=QVBoxLayout(w)
        inp=QComboBox(); inp.addItems(['Phone audio (if streamed)','PC microphone','USB interface'])
        gain=QSlider(Qt.Horizontal); gain.setRange(-24,24); gain.setValue(0)
        l.addWidget(self.card('Input',inp)); l.addWidget(self.card('Gain dB',gain)); l.addWidget(self.card('Audio note',QLabel('For the full external-mic/48 kHz capture path, use Solo mode on the Android app or a desktop audio interface.'))); l.addStretch(); return w
    def processing_page(self):
        w=QWidget(); l=QVBoxLayout(w)
        for title,a,b,val in [('Exposure',-100,100,0),('Contrast',-100,100,0),('Saturation',-100,100,0),('Sharpness',0,100,20)]:
            s=QSlider(Qt.Horizontal); s.setRange(a,b); s.setValue(val); l.addWidget(self.card(title,s))
        l.addWidget(QPushButton('FIT SOURCE')); l.addWidget(QPushButton('FILL / CROP')); l.addStretch(); return w
    def scopes_page(self):
        w=QWidget(); l=QVBoxLayout(w); l.addWidget(QLabel('Scopes are computed on the processed desktop frame.')); l.addWidget(QLabel('Histogram  •  Waveform  •  Vectorscope  •  False Color  •  Zebra')); l.addStretch(); return w
    def record_page(self):
        w=QWidget(); l=QVBoxLayout(w)
        self.rec_status=QLabel('NOT RECORDING'); self.rec_status.setObjectName('metric'); l.addWidget(self.rec_status)
        r=QPushButton('START DESKTOP RECORDING'); r.setObjectName('primary'); r.clicked.connect(self.toggle_record); l.addWidget(r)
        v=QPushButton('START VIRTUAL CAMERA'); v.clicked.connect(self.toggle_virtual); l.addWidget(v)
        l.addWidget(QLabel('The virtual camera appears to supported applications as a normal camera when an OS backend/driver is installed.'))
        l.addStretch(); return w
    def settings_page(self):
        w=QWidget(); l=QVBoxLayout(w); l.addWidget(QLabel('MoA Cam 1.0')); l.addWidget(QLabel('Built by Masters Of All')); l.addWidget(QLabel('Windows + Linux desktop controller')); l.addWidget(QLabel('Android camera / standalone cinema app')); l.addStretch(); return w
    def status(self,s): self.state.setText('● '+s.upper())
    def on_frame(self,frame):
        self.last=frame; self.processed=self.apply_processing(frame.copy());
        rgb=cv2.cvtColor(self.processed,cv2.COLOR_BGR2RGB); h,w,ch=rgb.shape; q=QImage(rgb.data,w,h,ch*w,QImage.Format_RGB888); pix=QPixmap.fromImage(q).scaled(self.preview.size(),Qt.KeepAspectRatio,Qt.SmoothTransformation); self.preview.setPixmap(pix)
        if self.virtual:
            try:self.virtual.send(cv2.cvtColor(self.processed,cv2.COLOR_BGR2RGB))
            except Exception: pass
        if self.record and self.recording:self.record.write(self.processed)
    def apply_processing(self,f):
        # Desktop processing is intentionally conservative; it can be expanded without changing the phone transport.
        return f
    def toggle_virtual(self):
        if self.virtual:
            try:self.virtual.close()
            except:pass
            self.virtual=None; return
        if pyvirtualcam is None: QMessageBox.warning(self,'Virtual camera','Install pyvirtualcam and an OS virtual-camera backend/driver first.'); return
        if self.processed is None: QMessageBox.warning(self,'Virtual camera','Connect the phone first.'); return
        h,w=self.processed.shape[:2]
        try:
            self.virtual=pyvirtualcam.Camera(width=w,height=h,fps=30,print_fps=False)
            QMessageBox.information(self,'MoA Cam','Virtual camera started. Select the MoA/virtual camera in OBS or another app.')
        except Exception as e: QMessageBox.warning(self,'Virtual camera',str(e)); self.virtual=None
    def toggle_record(self):
        if self.recording:
            self.recording=False
            if self.record:self.record.release()
            self.record=None; self.rec_status.setText('SAVED / STOPPED'); return
        if self.processed is None: QMessageBox.warning(self,'Record','Connect the phone first.'); return
        path,_=QFileDialog.getSaveFileName(self,'Save MoA Cam recording','MoACam_recording.mp4','MP4 Video (*.mp4)')
        if not path:return
        h,w=self.processed.shape[:2]; self.record=cv2.VideoWriter(path,cv2.VideoWriter_fourcc(*'mp4v'),30,(w,h)); self.recording=self.record.isOpened(); self.rec_status.setText('● RECORDING') if self.recording else self.rec_status.setText('RECORD FAILED')
    def closeEvent(self,e):
        self.receiver.disconnect()
        if self.virtual:
            try:self.virtual.close()
            except:pass
        if self.record:self.record.release()
        e.accept()

def main():
    app=QApplication(sys.argv); app.setStyleSheet(STYLE); w=MoACam(); w.show(); sys.exit(app.exec())
if __name__=='__main__': main()

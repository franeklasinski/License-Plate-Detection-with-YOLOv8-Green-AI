# 🚗 Detekcja Tablic Rejestracyjnych z YOLOv8 + OCR

Projekt wykorzystuje modele **YOLOv8** (Small i Nano) do detekcji tablic rejestracyjnych na zdjęciach i filmach, wraz z rozpoznawaniem tekstu za pomocą **EasyOCR**. Dodatkowo analizujemy wpływ treningu na środowisko przy użyciu **CodeCarbon**.

---

## 📋 Spis Treści

- [Funkcjonalności](#-funkcjonalności)
- [Dataset](#-dataset)
- [Architektura](#-architektura)
- [Wyniki](#-wyniki)
- [Green AI](#-green-ai--codecarbon)
- [XAI - Heatmapy](#-xai---wyjaśnialność-modelu)
- [Instalacja](#-instalacja)
- [Użycie](#-użycie)
- [Struktura Projektu](#-struktura-projektu)

---

## 🎯 Funkcjonalności

✅ **Detekcja tablic rejestracyjnych** - YOLOv8s i YOLOv8n  
✅ **OCR** - Rozpoznawanie tekstu z EasyOCR (preprocessing: binaryzacja, kontrast, denoising)  
✅ **Wideo processing** - Przetwarzanie filmów z wykrywaniem tablic w czasie rzeczywistym  
✅ **Green AI** - Monitorowanie emisji CO2 (CodeCarbon)  
✅ **XAI** - Heatmapy aktywacji (pokazują co model "widzi")  
✅ **Porównanie modeli** - YOLOv8s vs YOLOv8n (metryki, emisje, wydajność)  

---
Projekt wykonany z pomocą Github Copilot , Franciszek Łasiński 

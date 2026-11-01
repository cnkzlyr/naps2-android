/**
 * NAPS2 Android - Ana Uygulama Mantığı ve Belge Yönetimi
 */

(function () {
  "use strict";

  // Durum Yönetimi
  const state = {
    pages: [], // { id, originalSrc, currentSrc, rotation, flipH, flipV, filter, threshold, brightness, contrast }
    selectedIds: new Set(),
    activeEditorIndex: -1,
    pdfSettings: {
      paperSize: "A4",
      orientation: "AUTO",
      margin: 14.17,
      filename: "",
      exportMode: "merge"
    }
  };

  // DOM Elemanları
  const dom = {
    pagesGrid: document.getElementById("pagesGrid"),
    emptyState: document.getElementById("emptyState"),
    pageCountLabel: document.getElementById("pageCountLabel"),
    fileInput: document.getElementById("fileInput"),
    profileSelect: document.getElementById("profileSelect"),
    // Butonlar
    btnCameraScan: document.getElementById("btnCameraScan"),
    btnEsclScan: document.getElementById("btnEsclScan"),
    btnImportFiles: document.getElementById("btnImportFiles"),
    btnSelectAll: document.getElementById("btnSelectAll"),
    btnDeleteSelected: document.getElementById("btnDeleteSelected"),
    btnRotateAll: document.getElementById("btnRotateAll"),
    btnSavePdf: document.getElementById("btnSavePdf"),
    btnShare: document.getElementById("btnShare"),
    btnOpenPdfSettings: document.getElementById("btnOpenPdfSettings"),
    // Modallar
    editorModal: document.getElementById("editorModal"),
    previewCanvas: document.getElementById("previewCanvas"),
    btnCloseEditor: document.getElementById("btnCloseEditor"),
    btnCancelEdit: document.getElementById("btnCancelEdit"),
    btnApplyEdit: document.getElementById("btnApplyEdit"),
    // Filtre Sliders
    thresholdSlider: document.getElementById("thresholdSlider"),
    thresholdVal: document.getElementById("thresholdVal"),
    bwSliderGroup: document.getElementById("bwSliderGroup"),
    brightnessSlider: document.getElementById("brightnessSlider"),
    brightnessVal: document.getElementById("brightnessVal"),
    contrastSlider: document.getElementById("contrastSlider"),
    contrastVal: document.getElementById("contrastVal"),
    btnEditRotateLeft: document.getElementById("btnEditRotateLeft"),
    btnEditRotateRight: document.getElementById("btnEditRotateRight"),
    btnEditFlipH: document.getElementById("btnEditFlipH"),
    // eSCL Modalı
    esclModal: document.getElementById("esclModal"),
    btnCloseEscl: document.getElementById("btnCloseEscl"),
    // PDF Ayarları Modalı
    pdfSettingsModal: document.getElementById("pdfSettingsModal"),
    btnClosePdfSettings: document.getElementById("btnClosePdfSettings"),
    btnSavePdfSettings: document.getElementById("btnSavePdfSettings")
  };

  // Başlatma
  function init() {
    bindEvents();
    NapsEscl.init();
    updateUI();
  }

  function bindEvents() {
    // Tarama & İçe Aktarma
    dom.btnCameraScan.addEventListener("click", () => {
      if (window.NapsNative && window.NapsNative.openCamera) {
        window.NapsNative.openCamera();
      } else {
        dom.fileInput.setAttribute("capture", "environment");
        dom.fileInput.click();
      }
    });

    dom.btnEsclScan.addEventListener("click", () => {
      openModal(dom.esclModal);
    });

    dom.btnImportFiles.addEventListener("click", () => {
      if (window.NapsNative && window.NapsNative.openFileChooser) {
        window.NapsNative.openFileChooser("*/*");
      } else {
        dom.fileInput.removeAttribute("capture");
        dom.fileInput.click();
      }
    });

    dom.fileInput.addEventListener("change", handleFileInputChange);

    // Seçim ve Üst Butonlar
    dom.btnSelectAll.addEventListener("click", toggleSelectAll);
    dom.btnDeleteSelected.addEventListener("click", deleteSelectedPages);
    dom.btnRotateAll.addEventListener("click", rotateSelectedPages);

    // Kaydetme & Paylaşma
    dom.btnSavePdf.addEventListener("click", handleSavePdf);
    dom.btnShare.addEventListener("click", handleSharePdf);
    dom.btnOpenPdfSettings.addEventListener("click", () => openModal(dom.pdfSettingsModal));

    // Modalları Kapatma
    dom.btnCloseEditor.addEventListener("click", () => closeModal(dom.editorModal));
    dom.btnCancelEdit.addEventListener("click", () => closeModal(dom.editorModal));
    dom.btnCloseEscl.addEventListener("click", () => closeModal(dom.esclModal));
    dom.btnClosePdfSettings.addEventListener("click", () => closeModal(dom.pdfSettingsModal));
    dom.btnSavePdfSettings.addEventListener("click", savePdfSettings);

    // Profil Değişimi
    dom.profileSelect.addEventListener("change", handleProfileChange);

    // Düzenleyici Olayları
    bindEditorEvents();
  }

  // Profil Değişimi
  function handleProfileChange() {
    const val = dom.profileSelect.value;
    if (val === "bw_doc") {
      state.pdfSettings.paperSize = "A4";
      state.pdfSettings.orientation = "AUTO";
      // Seçili tüm sayfalara Siyah-Beyaz filtresi uygula
      state.pages.forEach(p => {
        if (state.selectedIds.has(p.id)) {
          p.filter = "bw";
          p.threshold = 135;
          renderPageThumbnail(p);
        }
      });
      showToast("Hızlı Belge (Siyah-Beyaz A4) profili aktif.");
    } else if (val === "color_doc") {
      state.pdfSettings.paperSize = "A4";
      state.pdfSettings.orientation = "AUTO";
      showToast("Renkli Resmi Belge profili aktif.");
    } else if (val === "uyap") {
      state.pdfSettings.paperSize = "A4";
      state.pdfSettings.orientation = "PORTRAIT";
      state.pdfSettings.margin = 14.17;
      showToast("UYAP / Dava Evrakı A4 profili aktif.");
    }
  }

  // Sayfa Ekleme
  function addPage(dataUrl) {
    const newPage = {
      id: "page_" + Date.now() + "_" + Math.random().toString(36).substr(2, 5),
      originalSrc: dataUrl,
      currentSrc: dataUrl,
      rotation: 0,
      flipH: false,
      flipV: false,
      filter: "original",
      threshold: 128,
      brightness: 0,
      contrast: 0
    };

    // Eğer Siyah-Beyaz profil aktifse doğrudan uygula
    if (dom.profileSelect.value === "bw_doc") {
      newPage.filter = "bw";
      newPage.threshold = 135;
    }

    state.pages.push(newPage);
    state.selectedIds.add(newPage.id);

    renderPageThumbnail(newPage, () => {
      updateUI();
    });
  }

  // Dosya Yükleme (Input File)
  function handleFileInputChange(e) {
    const files = Array.from(e.target.files);
    if (!files.length) return;

    files.forEach(file => {
      const ext = file.name.split(".").pop().toLowerCase();

      if (ext === "udf") {
        // UDF Belgesi
        const reader = new FileReader();
        reader.onload = function (ev) {
          if (window.NapsNative && window.NapsNative.parseUdf) {
            window.NapsNative.parseUdf(ev.target.result);
          } else {
            showToast("UDF dosyası yüklendi. Çözümleniyor...");
          }
        };
        reader.readAsDataURL(file);
      } else {
        // Görsel veya PDF
        const reader = new FileReader();
        reader.onload = function (ev) {
          addPage(ev.target.result);
        };
        reader.readAsDataURL(file);
      }
    });

    e.target.value = "";
  }

  // Sayfa Küçük Resmini Oluştur (NapsImageFilters kullanarak)
  function renderPageThumbnail(page, callback) {
    const img = new Image();
    img.onload = function () {
      const canvas = NapsImageFilters.processImage(img, {
        rotation: page.rotation,
        flipH: page.flipH,
        flipV: page.flipV,
        filter: page.filter,
        threshold: page.threshold,
        brightness: page.brightness,
        contrast: page.contrast
      });
      page.currentSrc = canvas.toDataURL("image/jpeg", 0.88);
      if (callback) callback();
      else updateUI();
    };
    img.src = page.originalSrc;
  }

  // Arayüzü Güncelle (Grid Render)
  function updateUI() {
    const hasPages = state.pages.length > 0;
    dom.emptyState.style.display = hasPages ? "none" : "flex";
    dom.pagesGrid.style.display = hasPages ? "grid" : "none";

    dom.pageCountLabel.textContent = `${state.pages.length} Sayfa (${state.selectedIds.size} Seçili)`;

    dom.pagesGrid.innerHTML = "";

    state.pages.forEach((page, index) => {
      const isSelected = state.selectedIds.has(page.id);

      const card = document.createElement("div");
      card.className = "page-card" + (isSelected ? " selected" : "");
      card.dataset.id = page.id;

      card.innerHTML = `
        <div class="page-badge">${index + 1}</div>
        <input type="checkbox" class="page-select-checkbox" ${isSelected ? "checked" : ""}>
        <div class="page-thumb-wrapper">
          <img src="${page.currentSrc}" class="page-thumb-img" alt="Sayfa ${index + 1}">
        </div>
        <div class="page-card-footer">
          <button class="card-action-btn btn-rotate-page" title="90° Döndür">
            <svg viewBox="0 0 24 24"><path d="M7.11 8.53L5.7 7.11C4.8 8.27 4.24 9.61 4.07 11h2.02c.14-.87.49-1.72 1.02-2.47zM6.09 13H4.07c.17 1.39.72 2.73 1.62 3.89l1.41-1.42c-.52-.75-.87-1.6-1.01-2.47zm1.01 5.32c1.16.9 2.51 1.44 3.9 1.61V17.9c-.87-.15-1.71-.49-2.46-1.03L7.1 18.32zM13 4.07V1L8.45 5.55 13 10V6.09c3.37 0 6.09 2.72 6.09 6.09s-2.72 6.09-6.09 6.09v2.02c4.49 0 8.11-3.62 8.11-8.11S17.49 4.07 13 4.07z"/></svg>
          </button>
          <button class="card-action-btn btn-edit-page" title="Düzenle / Filtrele">
            <svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
          </button>
          <button class="card-action-btn btn-move-up" title="Yukarı Taşı">
            <svg viewBox="0 0 24 24"><path d="M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z"/></svg>
          </button>
          <button class="card-action-btn btn-move-down" title="Aşağı Taşı">
            <svg viewBox="0 0 24 24"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"/></svg>
          </button>
          <button class="card-action-btn btn-delete-page" title="Sil">
            <svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
          </button>
        </div>
      `;

      // Checkbox Olayı
      const cb = card.querySelector(".page-select-checkbox");
      cb.addEventListener("change", (e) => {
        if (e.target.checked) state.selectedIds.add(page.id);
        else state.selectedIds.delete(page.id);
        updateUI();
      });

      // Kart Tıklama (Aç / Düzenle)
      card.querySelector(".page-thumb-wrapper").addEventListener("click", () => {
        openEditor(index);
      });

      // Hızlı Döndür
      card.querySelector(".btn-rotate-page").addEventListener("click", (e) => {
        e.stopPropagation();
        page.rotation = (page.rotation + 90) % 360;
        renderPageThumbnail(page);
      });

      // Düzenle
      card.querySelector(".btn-edit-page").addEventListener("click", (e) => {
        e.stopPropagation();
        openEditor(index);
      });

      // Yukarı Taşı
      card.querySelector(".btn-move-up").addEventListener("click", (e) => {
        e.stopPropagation();
        if (index > 0) {
          const tmp = state.pages[index - 1];
          state.pages[index - 1] = state.pages[index];
          state.pages[index] = tmp;
          updateUI();
        }
      });

      // Aşağı Taşı
      card.querySelector(".btn-move-down").addEventListener("click", (e) => {
        e.stopPropagation();
        if (index < state.pages.length - 1) {
          const tmp = state.pages[index + 1];
          state.pages[index + 1] = state.pages[index];
          state.pages[index] = tmp;
          updateUI();
        }
      });

      // Sil
      card.querySelector(".btn-delete-page").addEventListener("click", (e) => {
        e.stopPropagation();
        state.pages.splice(index, 1);
        state.selectedIds.delete(page.id);
        updateUI();
      });

      dom.pagesGrid.appendChild(card);
    });
  }

  // Tümünü Seç / Kaldır
  function toggleSelectAll() {
    if (state.selectedIds.size === state.pages.length) {
      state.selectedIds.clear();
    } else {
      state.pages.forEach(p => state.selectedIds.add(p.id));
    }
    updateUI();
  }

  // Seçilenleri Sil
  function deleteSelectedPages() {
    if (state.selectedIds.size === 0) return;
    state.pages = state.pages.filter(p => !state.selectedIds.has(p.id));
    state.selectedIds.clear();
    updateUI();
    showToast("Seçilen sayfalar silindi.");
  }

  // Seçilenleri 90° Döndür
  function rotateSelectedPages() {
    if (state.selectedIds.size === 0) return;
    state.pages.forEach(p => {
      if (state.selectedIds.has(p.id)) {
        p.rotation = (p.rotation + 90) % 360;
        renderPageThumbnail(p);
      }
    });
    showToast("Seçilen sayfalar döndürüldü.");
  }

  // ==========================================
  // GÖRÜNTÜ DÜZENLEYİCİ (EDITOR) MANTIĞI
  // ==========================================
  let editorDraft = {};

  function openEditor(index) {
    if (index < 0 || index >= state.pages.length) return;
    state.activeEditorIndex = index;
    const page = state.pages[index];

    editorDraft = {
      rotation: page.rotation,
      flipH: page.flipH,
      flipV: page.flipV,
      filter: page.filter,
      threshold: page.threshold,
      brightness: page.brightness,
      contrast: page.contrast
    };

    // Sliderları ayarla
    dom.thresholdSlider.value = editorDraft.threshold;
    dom.thresholdVal.textContent = editorDraft.threshold;
    dom.brightnessSlider.value = editorDraft.brightness;
    dom.brightnessVal.textContent = editorDraft.brightness;
    dom.contrastSlider.value = editorDraft.contrast;
    dom.contrastVal.textContent = editorDraft.contrast;

    // Filtre butonunu aktif yap
    document.querySelectorAll(".filter-btn").forEach(btn => {
      btn.classList.toggle("active", btn.dataset.filter === editorDraft.filter);
    });
    dom.bwSliderGroup.style.display = editorDraft.filter === "bw" ? "block" : "none";

    document.getElementById("editPageTitle").textContent = `Sayfa ${index + 1} Düzenle (NAPS2 Motoru)`;

    renderEditorPreview();
    openModal(dom.editorModal);
  }

  function renderEditorPreview() {
    const page = state.pages[state.activeEditorIndex];
    if (!page) return;

    const img = new Image();
    img.onload = function () {
      const processedCanvas = NapsImageFilters.processImage(img, editorDraft);
      const targetCanvas = dom.previewCanvas;
      targetCanvas.width = processedCanvas.width;
      targetCanvas.height = processedCanvas.height;
      const ctx = targetCanvas.getContext("2d");
      ctx.drawImage(processedCanvas, 0, 0);
    };
    img.src = page.originalSrc;
  }

  function bindEditorEvents() {
    // Filtre Butonları
    document.querySelectorAll(".filter-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        document.querySelectorAll(".filter-btn").forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        editorDraft.filter = btn.dataset.filter;
        dom.bwSliderGroup.style.display = editorDraft.filter === "bw" ? "block" : "none";
        renderEditorPreview();
      });
    });

    // Siyah-Beyaz Eşik Slider
    dom.thresholdSlider.addEventListener("input", (e) => {
      editorDraft.threshold = parseInt(e.target.value, 10);
      dom.thresholdVal.textContent = editorDraft.threshold;
      renderEditorPreview();
    });

    // Parlaklık Slider
    dom.brightnessSlider.addEventListener("input", (e) => {
      editorDraft.brightness = parseInt(e.target.value, 10);
      dom.brightnessVal.textContent = editorDraft.brightness;
      renderEditorPreview();
    });

    // Kontrast Slider
    dom.contrastSlider.addEventListener("input", (e) => {
      editorDraft.contrast = parseInt(e.target.value, 10);
      dom.contrastVal.textContent = editorDraft.contrast;
      renderEditorPreview();
    });

    // Döndürme & Aynalama
    dom.btnEditRotateLeft.addEventListener("click", () => {
      editorDraft.rotation = (editorDraft.rotation + 270) % 360;
      renderEditorPreview();
    });
    dom.btnEditRotateRight.addEventListener("click", () => {
      editorDraft.rotation = (editorDraft.rotation + 90) % 360;
      renderEditorPreview();
    });
    dom.btnEditFlipH.addEventListener("click", () => {
      editorDraft.flipH = !editorDraft.flipH;
      renderEditorPreview();
    });

    // Uygula Butonu
    dom.btnApplyEdit.addEventListener("click", () => {
      const page = state.pages[state.activeEditorIndex];
      if (page) {
        Object.assign(page, editorDraft);
        renderPageThumbnail(page, () => {
          closeModal(dom.editorModal);
          updateUI();
          showToast("Filtre başarıyla uygulandı.");
        });
      }
    });
  }

  // ==========================================
  // PDF ÇIKTI VE PAYLAŞIM
  // ==========================================
  function handleSavePdf() {
    if (state.pages.length === 0) {
      showToast("Lütfen önce belge veya görsel ekleyin!");
      return;
    }

    const pagesToExport = state.selectedIds.size > 0
      ? state.pages.filter(p => state.selectedIds.has(p.id))
      : state.pages;

    const pagesData = pagesToExport.map(p => p.currentSrc);

    if (window.NapsNative && window.NapsNative.savePdf) {
      showToast("A4 PDF motoru çalışıyor...");
      window.NapsNative.savePdf(JSON.stringify(pagesData), JSON.stringify(state.pdfSettings));
    } else {
      showToast(`Tarayıcı ortamında ${pagesToExport.length} sayfa A4 PDF olarak işlendi.`);
    }
  }

  function handleSharePdf() {
    if (state.pages.length === 0) {
      showToast("Paylaşılacak belge yok!");
      return;
    }

    const pagesToExport = state.selectedIds.size > 0
      ? state.pages.filter(p => state.selectedIds.has(p.id))
      : state.pages;

    const pagesData = pagesToExport.map(p => p.currentSrc);

    if (window.NapsNative && window.NapsNative.sharePdf) {
      window.NapsNative.sharePdf(JSON.stringify(pagesData), JSON.stringify(state.pdfSettings));
    } else {
      showToast("Paylaşım menüsü açılıyor...");
    }
  }

  function savePdfSettings() {
    state.pdfSettings.paperSize = document.getElementById("pdfPaperSize").value;
    state.pdfSettings.orientation = document.getElementById("pdfOrientation").value;
    state.pdfSettings.margin = parseFloat(document.getElementById("pdfMargin").value);
    state.pdfSettings.filename = document.getElementById("pdfFilename").value.trim();
    state.pdfSettings.exportMode = document.getElementById("pdfExportMode").value;

    closeModal(dom.pdfSettingsModal);
    showToast("PDF ayarları kaydedildi.");
  }

  // Yardımcı Modal Açma / Kapama
  function openModal(modal) {
    if (modal) modal.classList.add("active");
  }

  function closeModal(modal) {
    if (modal) modal.classList.remove("active");
  }

  function showToast(msg) {
    if (window.NapsNative && window.NapsNative.showToast) {
      window.NapsNative.showToast(msg);
    } else {
      console.log("[TOAST]", msg);
    }
  }

  // ==========================================
  // ANDROID NATIVE KÖPRÜ DİNLİYİCİLERİ
  // ==========================================
  window.onPageImported = function (dataUrl) {
    addPage(dataUrl);
    showToast("Sayfa başarıyla eklendi.");
  };

  window.onScannedPageReceived = function (dataUrl) {
    addPage(dataUrl);
    closeModal(dom.esclModal);
    showToast("Ağ tarayıcısından sayfa alındı!");
  };

  window.onUdfParsed = function (jsonStr) {
    try {
      const pageList = JSON.parse(jsonStr);
      pageList.forEach(pData => addPage(pData));
      showToast(`UDF belgesinden ${pageList.length} sayfa eklendi.`);
    } catch (e) {
      console.error(e);
    }
  };

  window.onPdfSaved = function (filePath) {
    showToast("Kayıt tamamlandı: " + filePath);
  };

  // Android Geri Tuşu
  window.handleAndroidBack = function () {
    const activeModals = document.querySelectorAll(".modal.active");
    if (activeModals.length > 0) {
      activeModals.forEach(m => m.classList.remove("active"));
      return "handled";
    }
    return "false";
  };

  // Başlat
  document.addEventListener("DOMContentLoaded", init);

})();

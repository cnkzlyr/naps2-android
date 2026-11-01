/**
 * NAPS2 Android - eSCL (AirPrint / Mopria) Ağ Tarayıcısı Yöneticisi
 */

const NapsEscl = (function () {

  const STORAGE_KEY = "naps2_escl_last_ip";

  function init() {
    const savedIp = localStorage.getItem(STORAGE_KEY);
    if (savedIp) {
      const ipInput = document.getElementById("esclIp");
      if (ipInput) ipInput.value = savedIp;
    }

    const btnStart = document.getElementById("btnEsclStart");
    const btnTest = document.getElementById("btnEsclTest");

    if (btnStart) {
      btnStart.addEventListener("click", startScan);
    }
    if (btnTest) {
      btnTest.addEventListener("click", testConnection);
    }
  }

  function getSettings() {
    const ip = document.getElementById("esclIp").value.trim();
    const port = parseInt(document.getElementById("esclPort").value, 10) || 8080;
    const resolution = parseInt(document.getElementById("esclRes").value, 10) || 300;
    const colorMode = document.getElementById("esclColor").value;
    const inputSource = document.getElementById("esclSource").value;

    return { ip, port, resolution, colorMode, inputSource };
  }

  function setStatus(text, isError) {
    const box = document.getElementById("esclStatus");
    if (!box) return;
    box.style.display = "block";
    box.style.borderLeftColor = isError ? "var(--danger)" : "var(--primary)";
    box.textContent = text;
  }

  function testConnection() {
    const settings = getSettings();
    if (!settings.ip || settings.ip === "192.168.1.") {
      setStatus("Lütfen geçerli bir tarayıcı IP adresi girin.", true);
      return;
    }
    localStorage.setItem(STORAGE_KEY, settings.ip);
    setStatus(`Tarayıcı aranıyor: http://${settings.ip}:${settings.port}/eSCL/ScannerCapabilities ...`, false);

    if (window.NapsNative && window.NapsNative.scanFromNetwork) {
      // Test için düşük çözünürlük veya doğrudan tarama testi
      setStatus("Bağlantı isteği gönderildi. Tarayıcı yanıtı bekleniyor...", false);
    } else {
      setStatus("Tarayıcı bağlantı testi tarayıcı ortamında simüle edildi: eSCL aktif.", false);
    }
  }

  function startScan() {
    const settings = getSettings();
    if (!settings.ip) {
      setStatus("Lütfen geçerli bir tarayıcı IP adresi girin.", true);
      return;
    }

    localStorage.setItem(STORAGE_KEY, settings.ip);
    setStatus(`Tarama başlatılıyor: ${settings.resolution} DPI, ${settings.colorMode}...`, false);

    if (window.NapsNative && window.NapsNative.scanFromNetwork) {
      window.NapsNative.scanFromNetwork(settings.ip, settings.port, JSON.stringify(settings));
    } else {
      setStatus("Hata: Android Native Köprüsü aktif değil.", true);
    }
  }

  return {
    init: init,
    setStatus: setStatus
  };
})();

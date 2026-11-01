/**
 * NAPS2 Android - Yüksek Başarımlı Görüntü Filtreleme Motoru
 * NAPS2 algoritması: Siyah-Beyaz Eşikleme (Thresholding), Gri Tonlama,
 * Parlaklık, Kontrast ve Dönüşüm Matrisleri.
 */

const NapsImageFilters = (function () {

  function processImage(srcImage, options) {
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d", { willReadFrequently: true });

    let w = srcImage.naturalWidth || srcImage.width;
    let h = srcImage.naturalHeight || srcImage.height;

    const rotation = (options.rotation || 0) % 360;
    const isSideways = rotation === 90 || rotation === 270;

    canvas.width = isSideways ? h : w;
    canvas.height = isSideways ? w : h;

    ctx.save();
    ctx.translate(canvas.width / 2, canvas.height / 2);

    if (rotation !== 0) {
      ctx.rotate((rotation * Math.PI) / 180);
    }
    if (options.flipH) {
      ctx.scale(-1, 1);
    }
    if (options.flipV) {
      ctx.scale(1, -1);
    }

    ctx.drawImage(srcImage, -w / 2, -h / 2);
    ctx.restore();

    // Piksel seviyesi filtreler (Threshold, B/C, Grayscale)
    const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const data = imgData.data;

    const filterType = options.filter || "original";
    const threshold = options.threshold !== undefined ? options.threshold : 128;
    const brightness = options.brightness || 0; // -100 to +100
    const contrast = options.contrast || 0;     // -100 to +100

    // Kontrast çarpanı: factor = (259 * (contrast + 255)) / (255 * (259 - contrast))
    const cFactor = (259 * (contrast + 255)) / (255 * (259 - contrast));

    for (let i = 0; i < data.length; i += 4) {
      let r = data[i];
      let g = data[i + 1];
      let b = data[i + 2];

      // 1. Parlaklık ve Kontrast uygula
      if (brightness !== 0) {
        r += brightness;
        g += brightness;
        b += brightness;
      }
      if (contrast !== 0) {
        r = cFactor * (r - 128) + 128;
        g = cFactor * (g - 128) + 128;
        b = cFactor * (b - 128) + 128;
      }

      // Sınırları sabitle (0-255)
      r = r < 0 ? 0 : r > 255 ? 255 : r;
      g = g < 0 ? 0 : g > 255 ? 255 : g;
      b = b < 0 ? 0 : b > 255 ? 255 : b;

      // 2. NAPS2 Mod Filtreleri
      if (filterType === "bw") {
        // NAPS2 Siyah-Beyaz Eşikleme (B&W Threshold)
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        const val = lum >= threshold ? 255 : 0;
        data[i] = val;
        data[i + 1] = val;
        data[i + 2] = val;
      } else if (filterType === "gray") {
        // Gri Tonlama (Grayscale)
        const lum = 0.299 * r + 0.587 * g + 0.114 * b;
        data[i] = lum;
        data[i + 1] = lum;
        data[i + 2] = lum;
      } else if (filterType === "invert") {
        // Renk Ters Çevirme (Invert)
        data[i] = 255 - r;
        data[i + 1] = 255 - g;
        data[i + 2] = 255 - b;
      } else {
        // Orijinal renkler (parlaklık ve kontrast uygulanmış)
        data[i] = r;
        data[i + 1] = g;
        data[i + 2] = b;
      }
    }

    ctx.putImageData(imgData, 0, 0);
    return canvas;
  }

  return {
    processImage: processImage
  };
})();

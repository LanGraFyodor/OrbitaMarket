import { chromium } from "playwright-core";

const baseUrl = process.env.ORBITAMARKET_URL || "http://localhost:3000";
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

const browser = await chromium.launch({
  executablePath: edgePath,
  headless: true,
});

try {
  const page = await browser.newPage({
    viewport: { width: 1920, height: 1080 },
  });
  const browserErrors = [];
  const geoResponses = [];
  let successfulTiles = 0;
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("response", async (response) => {
    if (response.url().includes("/geo/api/v1/geo/tiles/") && response.ok()) {
      successfulTiles += 1;
    }
    if (response.url().includes("/geo/api/v1/geo/analyze")) {
      geoResponses.push(`${response.status()} ${await response.text()}`);
    }
  });

  const email = `map-check-${Date.now()}@orbitamarket.local`;
  const registration = await page.request.post(
    `${baseUrl}/auth/api/v1/auth/register`,
    {
      data: {
        email,
        password: "MapCheck-2026!",
        display_name: "Map QA",
      },
    },
  );
  if (!registration.ok()) {
    throw new Error(
      `Registration failed: ${registration.status()} ${await registration.text()}`,
    );
  }
  const session = await registration.json();

  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await page.evaluate((value) => {
    localStorage.setItem("orbitamarket-session", JSON.stringify(value));
  }, session);
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.evaluate(() => {
    location.hash = "mission";
  });
  await page.addStyleTag({
    content:
      "html{scroll-behavior:auto!important}*{animation:none!important;transition:none!important}",
  });

  const orderButton = page.locator(".mission-summary .order-button");
  try {
    await orderButton.waitFor();
  } catch (error) {
    const body = (await page.locator("body").innerText()).slice(0, 1200);
    throw new Error(
      `Mission UI did not open. URL=${page.url()} BODY=${body}\n${error.message}`,
    );
  }
  if (!(await orderButton.isDisabled())) {
    throw new Error("Order button must be disabled before AOI selection");
  }
  await page
    .locator(".product-selector button")
    .filter({ hasText: "Съёмка" })
    .click();
  await page.getByRole("button", { name: "Выбрать AOI" }).click();

  const mapElement = page.locator(".leaflet-container");
  await mapElement.waitFor({ state: "visible" });
  await mapElement.scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  const box = await mapElement.boundingBox();
  if (!box) throw new Error("Map canvas has no bounding box");

  await page.locator(".leaflet-control-zoom-in").click();
  await page.waitForTimeout(300);
  await page.mouse.move(box.x + box.width * 0.75, box.y + box.height * 0.55);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.62, box.y + box.height * 0.48, {
    steps: 8,
  });
  await page.mouse.up();

  const points = [
    [box.width * 0.35, box.height * 0.36],
    [box.width * 0.66, box.height * 0.39],
    [box.width * 0.62, box.height * 0.68],
    [box.width * 0.39, box.height * 0.65],
  ];
  for (const [x, y] of points) {
    await page.mouse.click(box.x + x, box.y + y);
  }

  const finishButton = page.getByRole("button", { name: /Готово · 4/ });
  try {
    await finishButton.click();
  } catch (error) {
    const controls = await page.locator(".map-mode").innerText();
    const help = await page.locator(".map-help").innerText();
    throw new Error(
      `AOI clicks were not recorded. CONTROLS=${controls} HELP=${help}\n${error.message}`,
    );
  }
  await page.getByText("RUST GEO ENGINE").waitFor();
  try {
    await page.waitForFunction(() => {
      const button = document.querySelector(".mission-summary .order-button");
      return (
        button &&
        !button.disabled &&
        button.textContent.includes("Отправить миссию")
      );
    });
  } catch (error) {
    const summary = await page.locator(".mission-summary").innerText();
    throw new Error(
      `Quote did not unlock order. SUMMARY=${summary} GEO=${geoResponses.join(" | ")}\n${error.message}`,
    );
  }

  const authHeaders = {
    Authorization: `Bearer ${session.access_token}`,
  };
  const account = await page.request.post(
    `${baseUrl}/payments/api/v1/payments/accounts`,
    { headers: authHeaders },
  );
  if (![200, 201].includes(account.status())) {
    throw new Error(
      `Account preparation failed: ${account.status()} ${await account.text()}`,
    );
  }
  const topUp = await page.request.post(
    `${baseUrl}/payments/api/v1/payments/accounts/top-up`,
    {
      headers: authHeaders,
      data: { amount: 1_000_000 },
    },
  );
  if (!topUp.ok()) {
    throw new Error(`Top-up failed: ${topUp.status()} ${await topUp.text()}`);
  }

  await orderButton.click();
  const paidOrder = page
    .locator(".orders-table .table-row")
    .filter({ hasText: "Оплачен" })
    .first();
  await paidOrder.waitFor({ state: "visible", timeout: 30_000 });
  await paidOrder
    .getByTitle("Открыть спутниковый продукт")
    .click({ timeout: 30_000 });

  const productImage = page.locator(
    '.product-modal img[alt="Спутниковый продукт AOI"]',
  );
  await productImage.waitFor({ state: "visible", timeout: 45_000 });
  await page.waitForFunction(() => {
    const image = document.querySelector(
      '.product-modal img[alt="Спутниковый продукт AOI"]',
    );
    return (
      image instanceof HTMLImageElement &&
      image.complete &&
      image.naturalWidth > 0
    );
  });

  const productState = await page.evaluate(async () => {
    const localFrameKeys = Object.keys(localStorage).filter((key) =>
      key.startsWith("orbitamarket-frame-v2-"),
    );
    const storedFrames = await new Promise((resolve, reject) => {
      const request = indexedDB.open("orbitamarket-products", 1);
      request.onerror = () => reject(request.error);
      request.onsuccess = () => {
        const database = request.result;
        const transaction = database.transaction("frames", "readonly");
        const count = transaction.objectStore("frames").count();
        count.onerror = () => reject(count.error);
        count.onsuccess = () => {
          database.close();
          resolve(count.result);
        };
      };
    });
    return { localFrameKeys, storedFrames };
  });

  if (productState.localFrameKeys.length > 0) {
    throw new Error("Satellite image must not be persisted in localStorage");
  }
  if (productState.storedFrames < 1) {
    throw new Error("Satellite image Blob was not persisted in IndexedDB");
  }

  const tileState = await page
    .locator(".leaflet-tile-loaded")
    .evaluateAll((tiles) => ({
      visibleTiles: tiles.length,
      loadedImages: tiles.filter((tile) => tile.naturalWidth > 0).length,
    }));
  const visibleVertices = await page
    .locator(".leaflet-overlay-pane path")
    .count();

  await page.locator(".mission-builder").screenshot({
    path: `${process.env.TEMP || "."}\\orbitamarket-map-check.png`,
  });

  if (successfulTiles === 0)
    throw new Error("No map tiles were loaded successfully");
  if (tileState.loadedImages === 0)
    throw new Error("Map tile images are not visible");
  if (visibleVertices < 5)
    throw new Error("AOI vertices or polygon are not visible");

  if (browserErrors.length) {
    throw new Error(`Browser errors: ${browserErrors.join(" | ")}`);
  }

  console.log(
    JSON.stringify(
      {
        result: "PASS",
        orderEnabledAfterQuote: true,
        paidOrderOpened: true,
        indexedDbFrames: productState.storedFrames,
        successfulTiles,
        tileState,
        visibleVertices,
        screenshot: `${process.env.TEMP || "."}\\orbitamarket-map-check.png`,
      },
      null,
      2,
    ),
  );
} finally {
  await browser.close();
}

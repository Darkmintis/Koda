import { expect } from "@playwright/test";
import { WorkspacePage } from "./WorkspacePage";

export const WASM_FLAGS = [
  "enable-feature-render-wasm",
  "enable-render-wasm-dpr",
  "enable-feature-text-editor-v2",
];

export class WasmWorkspacePage extends WorkspacePage {
  static async init(page) {
    await super.init(page);
    await WorkspacePage.mockConfigFlags(page, WASM_FLAGS);

    await page.addInitScript(() => {
      document.addEventListener("koda:wasm:loaded", () => {
        globalThis.wasmModuleLoaded = true;
      });

      document.addEventListener("koda:wasm:render", () => {
        globalThis.wasmRenderCount = (globalThis.wasmRenderCount || 0) + 1;
      });

      document.addEventListener("koda:wasm:set-objects", () => {
        globalThis.wasmSetObjectsFinished = true;
      });
    });
  }

  constructor(page) {
    super(page);
    this.canvas = page.getByTestId("canvas-wasm-shapes");
  }

  async waitForFirstRender() {
    await this.pageName.waitFor();
    await this.canvas.waitFor();
    await this.page.waitForFunction(() => {
      console.log("RAF:", window.wasmSetObjectsFinished);
      return window.wasmSetObjectsFinished;
    });
  }

  async waitForFirstRenderWithoutUI() {
    await this.waitForFirstRender();
    await this.hideUI();
  }

  async hideUI() {
    await this.page.keyboard.press("\\");
    await expect(this.pageName).not.toBeVisible();
  }

  static async mockGoogleFont(page, fontSlug, assetFilename, options = {}) {
    const url = new RegExp(`/internal/gfonts/font/${fontSlug}`);
    return await page.route(url, (route) =>
      route.fulfill({
        status: 200,
        path: `playwright/data/${assetFilename}`,
        contentType: "application/font-ttf",
        ...options,
      }),
    );
  }

  async mockGoogleFont(fontSlug, assetFilename, options) {
    return WasmWorkspacePage.mockGoogleFont(
      this.page,
      fontSlug,
      assetFilename,
      options,
    );
  }
}

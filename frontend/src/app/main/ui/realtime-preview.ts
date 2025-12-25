/**
 * Koda - Real-Time Preview System
 *
 * Shows live generated code as designers work in Koda
 * Updates instantly without saving or manual generation
 */

import type { KodaDesign, KodaElement, GenerationOptions, CodeFile } from '../../../../../koda-ai/src/types/index.js';
import { DataDrivenGenerator } from '../../../../../koda-ai/src/generator/data-driven-generator.js';

// ============================================================================
// Preview System Types
// ============================================================================

export interface PreviewUpdate {
  elementId: string;
  changeType: 'create' | 'update' | 'delete' | 'move' | 'style';
  oldValue?: any;
  newValue: any;
  timestamp: number;
}

export interface PreviewState {
  design: KodaDesign;
  generatedCode: CodeFile[];
  selectedElement?: string;
  framework: 'react' | 'vue' | 'html-css' | 'react-native';
  lastUpdate: number;
  isGenerating: boolean;
}

// ============================================================================
// Real-Time Preview Manager
// ============================================================================

export class RealtimePreviewManager {
  private generator: DataDrivenGenerator;
  private currentState: PreviewState;
  private subscribers: Set<(state: PreviewState) => void> = new Set();
  private updateQueue: PreviewUpdate[] = [];
  private isProcessing: boolean = false;
  private debounceTimer?: NodeJS.Timeout;

  constructor() {
    this.generator = new DataDrivenGenerator();
    this.currentState = {
      design: this.createEmptyDesign(),
      generatedCode: [],
      framework: 'react',
      lastUpdate: Date.now(),
      isGenerating: false
    };
  }

  // ============================================================================
  // Public API
  // ============================================================================

  /**
   * Initialize preview with a design
   */
  async initialize(design: KodaDesign, framework: string = 'react'): Promise<void> {
    this.currentState = {
      design: { ...design },
      generatedCode: [],
      framework: framework as any,
      lastUpdate: Date.now(),
      isGenerating: true
    };

    this.notifySubscribers();

    try {
      const options: GenerationOptions = {
        framework: framework as any,
        typescript: framework.includes('typescript'),
        cssFramework: 'tailwind'
      };

      const generatedCode = await this.generator.generate(design, options);

      this.currentState.generatedCode = generatedCode;
      this.currentState.isGenerating = false;
      this.currentState.lastUpdate = Date.now();

      this.notifySubscribers();
    } catch (error) {
      console.error('Preview generation failed:', error);
      this.currentState.isGenerating = false;
      this.currentState.generatedCode = [];
      this.notifySubscribers();
    }
  }

  /**
   * Update design element in real-time
   */
  async updateElement(update: PreviewUpdate): Promise<void> {
    // Add to queue for debounced processing
    this.updateQueue.push(update);

    // Clear existing timer
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
    }

    // Set debounced update (100ms delay)
    this.debounceTimer = setTimeout(() => {
      this.processUpdates();
    }, 100);
  }

  /**
   * Subscribe to preview updates
   */
  subscribe(callback: (state: PreviewState) => void): () => void {
    this.subscribers.add(callback);

    // Immediately send current state
    callback(this.currentState);

    // Return unsubscribe function
    return () => {
      this.subscribers.delete(callback);
    };
  }

  /**
   * Change framework and regenerate
   */
  async changeFramework(framework: string): Promise<void> {
    if (framework === this.currentState.framework) return;

    this.currentState.framework = framework as any;
    this.currentState.isGenerating = true;
    this.notifySubscribers();

    await this.regenerate();
  }

  /**
   * Get code for specific element
   */
  getElementCode(elementId: string): string | null {
    // Find component file that contains this element
    const componentFile = this.currentState.generatedCode.find(file =>
      file.component && this.containsElement(file.content, elementId)
    );

    if (componentFile) {
      return this.extractElementCode(componentFile.content, elementId);
    }

    return null;
  }

  /**
   * Copy element code to clipboard
   */
  async copyElementCode(elementId: string): Promise<boolean> {
    const code = this.getElementCode(elementId);
    if (!code) return false;

    try {
      await navigator.clipboard.writeText(code);
      return true;
    } catch (error) {
      console.error('Failed to copy code:', error);
      return false;
    }
  }

  /**
   * Get formatted code for display
   */
  getFormattedCode(filePath?: string): string {
    if (filePath) {
      const file = this.currentState.generatedCode.find(f => f.path === filePath);
      return file ? this.formatCodeForDisplay(file.content, file.language) : '';
    }

    // Return main component code by default
    const mainFile = this.currentState.generatedCode.find(f =>
      f.path.includes('App.') || f.type === 'component'
    );
    return mainFile ? this.formatCodeForDisplay(mainFile.content, mainFile.language) : '';
  }

  // ============================================================================
  // Private Methods
  // ============================================================================

  private async processUpdates(): Promise<void> {
    if (this.isProcessing || this.updateQueue.length === 0) return;

    this.isProcessing = true;
    this.currentState.isGenerating = true;
    this.notifySubscribers();

    try {
      // Apply all queued updates to design
      for (const update of this.updateQueue) {
        this.applyUpdateToDesign(update);
      }

      // Clear queue
      this.updateQueue = [];

      // Regenerate code
      await this.regenerate();

    } catch (error) {
      console.error('Failed to process updates:', error);
    } finally {
      this.isProcessing = false;
    }
  }

  private applyUpdateToDesign(update: PreviewUpdate): void {
    // Find element in design
    const element = this.findElementInDesign(update.elementId);
    if (!element) return;

    switch (update.changeType) {
      case 'update':
        if (update.newValue.x !== undefined) element.x = update.newValue.x;
        if (update.newValue.y !== undefined) element.y = update.newValue.y;
        if (update.newValue.width !== undefined) element.width = update.newValue.width;
        if (update.newValue.height !== undefined) element.height = update.newValue.height;
        if (update.newValue.fills) element.fills = update.newValue.fills;
        if (update.newValue.textContent !== undefined) element.textContent = update.newValue.textContent;
        break;

      case 'move':
        element.x = update.newValue.x;
        element.y = update.newValue.y;
        break;

      case 'style':
        Object.assign(element, update.newValue);
        break;
    }
  }

  private async regenerate(): Promise<void> {
    try {
      const options: GenerationOptions = {
        framework: this.currentState.framework,
        typescript: this.currentState.framework.includes('typescript'),
        cssFramework: 'tailwind'
      };

      const generatedCode = await this.generator.generate(this.currentState.design, options);

      this.currentState.generatedCode = generatedCode;
      this.currentState.isGenerating = false;
      this.currentState.lastUpdate = Date.now();

      this.notifySubscribers();
    } catch (error) {
      console.error('Regeneration failed:', error);
      this.currentState.isGenerating = false;
      this.notifySubscribers();
    }
  }

  private findElementInDesign(elementId: string): KodaElement | null {
    // Search recursively through all pages and frames
    for (const page of this.currentState.design.pages) {
      for (const frame of page.frames) {
        const found = this.findElementInFrame(frame, elementId);
        if (found) return found;
      }
    }

    // Search components
    for (const component of this.currentState.design.components) {
      const found = this.findElementInFrame(component.element, elementId);
      if (found) return found;
    }

    return null;
  }

  private findElementInFrame(frame: any, elementId: string): KodaElement | null {
    if (frame.id === elementId) return frame;

    if (frame.children) {
      for (const child of frame.children) {
        const found = this.findElementInFrame(child, elementId);
        if (found) return found;
      }
    }

    return null;
  }

  private containsElement(code: string, elementId: string): boolean {
    // Check if code contains references to this element
    const patterns = [
      elementId,
      this.toCamelCase(elementId),
      this.toPascalCase(elementId),
      this.toKebabCase(elementId)
    ];

    return patterns.some(pattern => code.includes(pattern));
  }

  private extractElementCode(code: string, elementId: string): string {
    // Extract JSX/TSX element code (simplified)
    const lines = code.split('\n');
    const startPattern = new RegExp(`<${this.toPascalCase(elementId)}|id="${elementId}"`);
    const endPattern = /<\/\w+>|\/>/;

    let startIndex = -1;
    let braceCount = 0;

    for (let i = 0; i < lines.length; i++) {
      if (startPattern.test(lines[i])) {
        startIndex = i;
        break;
      }
    }

    if (startIndex === -1) return '';

    // Extract element (simplified - would need proper JSX parsing)
    let extractedLines: string[] = [];
    let inElement = false;

    for (let i = startIndex; i < lines.length; i++) {
      const line = lines[i];

      if (startPattern.test(line)) {
        inElement = true;
      }

      if (inElement) {
        extractedLines.push(line);

        // Check for element end
        if (endPattern.test(line)) {
          break;
        }
      }
    }

    return extractedLines.join('\n');
  }

  private formatCodeForDisplay(code: string, language: string): string {
    // Basic syntax highlighting simulation
    // In real implementation, would use a proper syntax highlighter
    return code
      .replace(/(import|export|function|const|let|var)/g, '**$1**') // Keywords
      .replace(/(<[^>]+>)/g, '`$1`') // JSX tags
      .replace(/('[^']*'|"[^"]*")/g, '*$1*'); // Strings
  }

  private notifySubscribers(): void {
    for (const subscriber of this.subscribers) {
      try {
        subscriber({ ...this.currentState });
      } catch (error) {
        console.error('Preview subscriber error:', error);
      }
    }
  }

  private createEmptyDesign(): KodaDesign {
    return {
      id: 'empty',
      name: 'Empty Design',
      version: '1.0.0',
      pages: [],
      components: [],
      tokens: {
        colors: new Map(),
        typography: new Map(),
        spacing: new Map(),
        shadows: new Map(),
        borders: new Map(),
        breakpoints: new Map()
      },
      metadata: {
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        exportedAt: new Date().toISOString(),
        kodaVersion: '1.0.0'
      }
    };
  }

  private toCamelCase(str: string): string {
    return str.replace(/[-_](.)/g, (_, letter) => letter.toUpperCase());
  }

  private toPascalCase(str: string): string {
    const camel = this.toCamelCase(str);
    return camel.charAt(0).toUpperCase() + camel.slice(1);
  }

  private toKebabCase(str: string): string {
    return str
      .replace(/([a-z])([A-Z])/g, '$1-$2')
      .replace(/[\s_]+/g, '-')
      .toLowerCase();
  }
}

// ============================================================================
// React Hook for Preview Integration
// ============================================================================

export function useRealtimePreview() {
  const [previewState, setPreviewState] = React.useState<PreviewState | null>(null);
  const previewManager = React.useRef<RealtimePreviewManager>();

  React.useEffect(() => {
    previewManager.current = new RealtimePreviewManager();

    const unsubscribe = previewManager.current.subscribe((state) => {
      setPreviewState(state);
    });

    return unsubscribe;
  }, []);

  const initializePreview = React.useCallback(async (design: KodaDesign, framework?: string) => {
    if (previewManager.current) {
      await previewManager.current.initialize(design, framework);
    }
  }, []);

  const updateElement = React.useCallback(async (update: PreviewUpdate) => {
    if (previewManager.current) {
      await previewManager.current.updateElement(update);
    }
  }, []);

  const copyElementCode = React.useCallback(async (elementId: string) => {
    if (previewManager.current) {
      return await previewManager.current.copyElementCode(elementId);
    }
    return false;
  }, []);

  const changeFramework = React.useCallback(async (framework: string) => {
    if (previewManager.current) {
      await previewManager.current.changeFramework(framework);
    }
  }, []);

  return {
    previewState,
    initializePreview,
    updateElement,
    copyElementCode,
    changeFramework
  };
}

// Declare React for TypeScript
declare const React: any;

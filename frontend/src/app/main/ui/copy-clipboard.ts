/**
 * Koda - Copy to Clipboard System
 *
 * Enables one-click copying of individual components and elements
 * with proper formatting and dependencies
 */

import type { KodaElement, CodeFile } from '../../../../koda-ai/src/types/index.js';

// ============================================================================
// Copy System Types
// ============================================================================

export interface CopyOptions {
  includeImports?: boolean;
  includeStyles?: boolean;
  includeTypes?: boolean;
  formatCode?: boolean;
  targetFramework?: string;
}

export interface CopiedContent {
  code: string;
  language: string;
  dependencies: string[];
  styles?: string;
  types?: string;
}

export interface CopyResult {
  success: boolean;
  content?: CopiedContent;
  error?: string;
}

// ============================================================================
// Copy Manager
// ============================================================================

export class CopyManager {
  private generatedFiles: CodeFile[] = [];

  constructor(generatedFiles: CodeFile[] = []) {
    this.generatedFiles = generatedFiles;
  }

  /**
   * Update generated files (called when code is regenerated)
   */
  updateFiles(files: CodeFile[]): void {
    this.generatedFiles = files;
  }

  /**
   * Copy element code to clipboard
   */
  async copyElement(elementId: string, options: CopyOptions = {}): Promise<CopyResult> {
    try {
      const element = this.findElementById(elementId);
      if (!element) {
        return { success: false, error: 'Element not found' };
      }

      const content = this.generateElementCode(element, options);

      await this.copyToClipboard(content.code);

      return {
        success: true,
        content
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  /**
   * Copy component code to clipboard
   */
  async copyComponent(componentName: string, options: CopyOptions = {}): Promise<CopyResult> {
    try {
      const componentFile = this.generatedFiles.find(file =>
        file.component === componentName || file.path.includes(componentName)
      );

      if (!componentFile) {
        return { success: false, error: 'Component not found' };
      }

      const content = this.generateComponentCode(componentFile, options);

      await this.copyToClipboard(content.code);

      return {
        success: true,
        content
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  /**
   * Copy entire page code
   */
  async copyPage(options: CopyOptions = {}): Promise<CopyResult> {
    try {
      const appFile = this.generatedFiles.find(file =>
        file.path.includes('App.') || file.type === 'component'
      );

      if (!appFile) {
        return { success: false, error: 'App component not found' };
      }

      const content = this.generatePageCode(appFile, options);

      await this.copyToClipboard(content.code);

      return {
        success: true,
        content
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  /**
   * Copy styles for element/component
   */
  async copyStyles(identifier: string, options: CopyOptions = {}): Promise<CopyResult> {
    try {
      const styles = this.extractStyles(identifier);

      if (!styles) {
        return { success: false, error: 'Styles not found' };
      }

      await this.copyToClipboard(styles);

      return {
        success: true,
        content: {
          code: styles,
          language: 'css',
          dependencies: []
        }
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  /**
   * Get copy preview (what would be copied)
   */
  getCopyPreview(identifier: string, type: 'element' | 'component' | 'page' | 'styles', options: CopyOptions = {}): CopiedContent | null {
    try {
      switch (type) {
        case 'element':
          const element = this.findElementById(identifier);
          return element ? this.generateElementCode(element, options) : null;

        case 'component':
          const componentFile = this.generatedFiles.find(file =>
            file.component === identifier || file.path.includes(identifier)
          );
          return componentFile ? this.generateComponentCode(componentFile, options) : null;

        case 'page':
          const appFile = this.generatedFiles.find(file =>
            file.path.includes('App.') || file.type === 'component'
          );
          return appFile ? this.generatePageCode(appFile, options) : null;

        case 'styles':
          const styles = this.extractStyles(identifier);
          return styles ? {
            code: styles,
            language: 'css',
            dependencies: []
          } : null;

        default:
          return null;
      }
    } catch (error) {
      console.error('Failed to generate copy preview:', error);
      return null;
    }
  }

  // ============================================================================
  // Private Methods
  // ============================================================================

  private async copyToClipboard(text: string): Promise<void> {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      // Fallback for older browsers
      const textArea = document.createElement('textarea');
      textArea.value = text;
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      try {
        document.execCommand('copy');
      } finally {
        document.body.removeChild(textArea);
      }
    }
  }

  private findElementById(elementId: string): KodaElement | null {
    // Search through all generated files for element references
    // This is a simplified implementation
    for (const file of this.generatedFiles) {
      if (file.content.includes(elementId) || file.content.includes(this.toCamelCase(elementId))) {
        // In a real implementation, we would parse the AST to find the exact element
        return {
          id: elementId,
          name: elementId,
          type: 'rectangle',
          x: 0,
          y: 0,
          width: 100,
          height: 100,
          visible: true
        } as KodaElement;
      }
    }
    return null;
  }

  private generateElementCode(element: KodaElement, options: CopyOptions): CopiedContent {
    const framework = options.targetFramework || 'react';
    let code = '';
    let dependencies: string[] = [];

    switch (framework) {
      case 'react':
        code = this.generateReactElementCode(element);
        dependencies = ['react'];
        break;

      case 'vue':
        code = this.generateVueElementCode(element);
        dependencies = ['vue'];
        break;

      case 'html-css':
        code = this.generateHTMLElementCode(element);
        dependencies = [];
        break;

      case 'react-native':
        code = this.generateReactNativeElementCode(element);
        dependencies = ['react-native'];
        break;

      default:
        code = `// Element code for ${framework} not implemented yet`;
    }

    if (options.includeImports && dependencies.length > 0) {
      code = this.addImports(code, dependencies, framework);
    }

    if (options.formatCode) {
      code = this.formatCode(code, framework);
    }

    return {
      code,
      language: this.getLanguageFromFramework(framework),
      dependencies
    };
  }

  private generateComponentCode(componentFile: CodeFile, options: CopyOptions): CopiedContent {
    let code = componentFile.content;
    const dependencies = this.extractDependencies(componentFile);

    if (!options.includeImports) {
      // Remove import statements
      code = code.replace(/^import.*$/gm, '').trim();
    }

    if (options.includeStyles) {
      const styles = this.extractStyles(componentFile.component || '');
      if (styles) {
        code += '\n\n/* Styles */\n' + styles;
      }
    }

    if (options.includeTypes && componentFile.language === 'typescript') {
      // TypeScript types are already included in the file
    }

    if (options.formatCode) {
      code = this.formatCode(code, this.getFrameworkFromFile(componentFile));
    }

    return {
      code,
      language: componentFile.language,
      dependencies,
      styles: options.includeStyles ? this.extractStyles(componentFile.component || '') : undefined
    };
  }

  private generatePageCode(appFile: CodeFile, options: CopyOptions): CopiedContent {
    let code = appFile.content;
    const dependencies = this.extractDependencies(appFile);

    if (!options.includeImports) {
      code = code.replace(/^import.*$/gm, '').trim();
    }

    if (options.includeStyles) {
      // Include all styles from generated files
      const allStyles = this.generatedFiles
        .filter(f => f.type === 'style')
        .map(f => f.content)
        .join('\n\n');

      if (allStyles) {
        code += '\n\n/* Global Styles */\n' + allStyles;
      }
    }

    if (options.formatCode) {
      code = this.formatCode(code, this.getFrameworkFromFile(appFile));
    }

    return {
      code,
      language: appFile.language,
      dependencies
    };
  }

  // ============================================================================
  // Code Generation Helpers
  // ============================================================================

  private generateReactElementCode(element: KodaElement): string {
    const tagName = this.getReactTagName(element);
    const props = this.generateReactProps(element);

    return `<${tagName}${props}>
  {/* ${element.name} */}
</${tagName}>`;
  }

  private generateVueElementCode(element: KodaElement): string {
    const tagName = this.getVueTagName(element);
    const props = this.generateVueProps(element);

    return `<${tagName}${props}>
  <!-- ${element.name} -->
</${tagName}>`;
  }

  private generateHTMLElementCode(element: KodaElement): string {
    const tagName = this.getHTMLTagName(element);
    const attributes = this.generateHTMLAttributes(element);

    return `<${tagName}${attributes}>
  <!-- ${element.name} -->
</${tagName}>`;
  }

  private generateReactNativeElementCode(element: KodaElement): string {
    const componentName = this.getReactNativeComponentName(element);
    const props = this.generateReactNativeProps(element);

    return `<${componentName}${props}>
  {/* ${element.name} */}
</${componentName}>`;
  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  private getReactTagName(element: KodaElement): string {
    switch (element.type) {
      case 'text': return 'p';
      case 'image': return 'img';
      default: return 'div';
    }
  }

  private getVueTagName(element: KodaElement): string {
    return this.getReactTagName(element);
  }

  private getHTMLTagName(element: KodaElement): string {
    return this.getReactTagName(element);
  }

  private getReactNativeComponentName(element: KodaElement): string {
    switch (element.type) {
      case 'text': return 'Text';
      case 'image': return 'Image';
      default: return 'View';
    }
  }

  private generateReactProps(element: KodaElement): string {
    const props: string[] = [];

    const className = this.toKebabCase(element.name);
    props.push(`className="${className}"`);

    const styleProps = this.getInlineStyles(element);
    if (Object.keys(styleProps).length > 0) {
      props.push(`style={${JSON.stringify(styleProps)}}`);
    }

    return props.length > 0 ? ` ${props.join(' ')}` : '';
  }

  private generateVueProps(element: KodaElement): string {
    const props: string[] = [];

    const className = this.toKebabCase(element.name);
    props.push(`class="${className}"`);

    return props.length > 0 ? ` ${props.join(' ')}` : '';
  }

  private generateHTMLAttributes(element: KodaElement): string {
    const attrs: string[] = [];

    const className = this.toKebabCase(element.name);
    attrs.push(`class="${className}"`);

    return attrs.length > 0 ? ` ${attrs.join(' ')}` : '';
  }

  private generateReactNativeProps(element: KodaElement): string {
    const props: string[] = [];

    const styleName = `${this.toPascalCase(element.name)}Style`;
    props.push(`style={styles.${styleName}}`);

    return props.length > 0 ? ` ${props.join(' ')}` : '';
  }

  private getInlineStyles(element: KodaElement): Record<string, any> {
    const styles: Record<string, any> = {};

    // Position styles (exact pixel matching)
    styles.position = 'absolute';
    styles.left = `${element.x}px`;
    styles.top = `${element.y}px`;
    styles.width = `${element.width}px`;
    styles.height = `${element.height}px`;

    // Visual styles
    if (element.fills && element.fills.length > 0) {
      const fill = element.fills[0];
      if (fill.color?.hex) {
        styles.backgroundColor = fill.color.hex;
      }
    }

    if (element.cornerRadius) {
      const radius = Array.isArray(element.cornerRadius) ? element.cornerRadius[0] : element.cornerRadius;
      styles.borderRadius = `${radius}px`;
    }

    if (element.opacity !== undefined && element.opacity !== 1) {
      styles.opacity = element.opacity;
    }

    return styles;
  }

  private extractDependencies(file: CodeFile): string[] {
    const dependencies: string[] = [];
    const lines = file.content.split('\n');

    for (const line of lines) {
      const importMatch = line.match(/import\s+.*?\s+from\s+['"]([^'"]+)['"]/);
      if (importMatch) {
        dependencies.push(importMatch[1]);
      }
    }

    return dependencies;
  }

  private extractStyles(identifier: string): string | null {
    const styleFile = this.generatedFiles.find(file => file.type === 'style');
    if (!styleFile) return null;

    const selector = `.${this.toKebabCase(identifier)}`;
    const lines = styleFile.content.split('\n');

    let inBlock = false;
    let braceCount = 0;
    const styles: string[] = [];

    for (const line of lines) {
      if (line.includes(selector) && line.includes('{')) {
        inBlock = true;
        braceCount = (line.match(/{/g) || []).length - (line.match(/}/g) || []).length;
        styles.push(line);
      } else if (inBlock) {
        styles.push(line);
        braceCount += (line.match(/{/g) || []).length;
        braceCount -= (line.match(/}/g) || []).length;

        if (braceCount <= 0) {
          inBlock = false;
          break;
        }
      }
    }

    return styles.length > 0 ? styles.join('\n') : null;
  }

  private addImports(code: string, dependencies: string[], framework: string): string {
    let imports = '';

    for (const dep of dependencies) {
      switch (framework) {
        case 'react':
          if (dep === 'react') {
            imports += "import React from 'react';\n";
          } else {
            imports += `import ${dep} from '${dep}';\n`;
          }
          break;

        case 'react-native':
          imports += `import { ${dep} } from 'react-native';\n`;
          break;

        default:
          imports += `import ${dep} from '${dep}';\n`;
      }
    }

    return imports + '\n' + code;
  }

  private formatCode(code: string, framework: string): string {
    // Basic code formatting (in production, would use Prettier or similar)
    return code
      .replace(/\n{3,}/g, '\n\n') // Remove excessive newlines
      .replace(/[ \t]+$/gm, '') // Remove trailing whitespace
      .replace(/\n*$/, '\n'); // Ensure single trailing newline
  }

  private getLanguageFromFramework(framework: string): string {
    switch (framework) {
      case 'react':
      case 'react-native':
        return 'javascript';
      case 'vue':
        return 'html';
      case 'html-css':
        return 'html';
      case 'flutter':
        return 'dart';
      default:
        return 'text';
    }
  }

  private getFrameworkFromFile(file: CodeFile): string {
    if (file.path.includes('.jsx') || file.path.includes('.tsx')) return 'react';
    if (file.path.includes('.vue')) return 'vue';
    if (file.path.includes('.html')) return 'html-css';
    if (file.path.includes('.dart')) return 'flutter';
    return 'react'; // default
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
// Global Copy Manager Instance
// ============================================================================

export const copyManager = new CopyManager();

// ============================================================================
// React Hook for Copy Integration
// ============================================================================

export function useCopyToClipboard() {
  const copyElement = React.useCallback(async (
    elementId: string,
    options?: CopyOptions
  ): Promise<CopyResult> => {
    return await copyManager.copyElement(elementId, options);
  }, []);

  const copyComponent = React.useCallback(async (
    componentName: string,
    options?: CopyOptions
  ): Promise<CopyResult> => {
    return await copyManager.copyComponent(componentName, options);
  }, []);

  const copyPage = React.useCallback(async (
    options?: CopyOptions
  ): Promise<CopyResult> => {
    return await copyManager.copyPage(options);
  }, []);

  const copyStyles = React.useCallback(async (
    identifier: string,
    options?: CopyOptions
  ): Promise<CopyResult> => {
    return await copyManager.copyStyles(identifier, options);
  }, []);

  const getCopyPreview = React.useCallback((
    identifier: string,
    type: 'element' | 'component' | 'page' | 'styles',
    options?: CopyOptions
  ): CopiedContent | null => {
    return copyManager.getCopyPreview(identifier, type, options);
  }, []);

  return {
    copyElement,
    copyComponent,
    copyPage,
    copyStyles,
    getCopyPreview
  };
}

// Declare React for TypeScript
declare const React: any;

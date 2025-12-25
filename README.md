[uri_license]: https://www.mozilla.org/en-US/MPL/2.0
[uri_license_image]: https://img.shields.io/badge/MPL-2.0-blue.svg

# Koda

<p align="center">
<a href="https://www.mozilla.org/en-US/MPL/2.0" rel="nofollow"><img alt="License: MPL-2.0" src="https://img.shields.io/badge/MPL-2.0-blue.svg" style="max-width:100%;"></a>
</p>

<p align="center">
    <a href="https://koda.app/"><b>Website</b></a>  
    <a href="https://docs.koda.app/"><b>Documentation</b></a>
</p>

<br />

**Koda** is a powerful design-to-code platform that enables designers to create stunning designs, interactive prototypes, and design systems at scale-while developers get AI-powered, production-ready frontend code that matches their designs pixel-perfectly.

## Key Features

### Design Like Figma
Full-featured design editor with everything you need:
- Vector graphics and shape tools
- Components and variants
- Design tokens and design systems
- CSS Grid and Flexbox layouts
- Real-time collaboration
- Interactive prototyping

### Two Code Generation Approaches

#### 🎯 **Quick Component Export (Free)**
Get instant code snippets for individual components:
- **One-Click Copy**: Copy code for buttons, cards, forms, etc.
- **Multiple Formats**: React, Vue, HTML+CSS, Tailwind
- **Fast Prototyping**: Perfect for component libraries
- **Basic Styling**: Direct CSS from your design

#### 🚀 **AI Prototype-to-App Generation (Pro)**
Transform complete prototypes into production applications:
- **Full Application Code**: Complete React/Vue/Angular apps with routing
- **AI Intelligence**: Automatic optimization, accessibility, performance
- **Enterprise Features**: SOC 2 compliance, team collaboration, security
- **Production Ready**: Testing, error handling, deployment configs
- **Cross-Platform**: Web, Mobile (React Native, Flutter), Desktop (Electron)
- **Replaces Frontend Devs**: Design → Code → Deploy workflow

### Built for Teams
- Real-time collaboration
- Version history
- Design handoff with inspect mode
- API and webhooks integration

## Getting Started

Koda is available as a cloud SaaS platform at [koda.app](https://koda.app).

1. Sign up at [koda.app](https://koda.app)
2. Create your first project
3. Start designing
4. Generate code with AI

## Documentation

- [User Guide](https://docs.koda.app/user-guide/)
- [API Reference](https://docs.koda.app/api/)
- [Code Generation](https://docs.koda.app/code-gen/)

## Architecture

Koda is built with:
- **Backend**: Clojure (JVM) with PostgreSQL
- **Frontend**: ClojureScript with React
- **Render Engine**: Rust + WebAssembly
- **AI Engine**: Proprietary (private repository)

## Acknowledgments

Koda's design editor is built upon [Penpot](https://penpot.app), an open-source design platform by Kaleidos. We are grateful for their amazing work and contribute back to the open-source community.

## License

The design editor portion of this codebase is licensed under the Mozilla Public License 2.0 (MPL-2.0).

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.

Original work Copyright (c) KALEIDOS INC
Modified work Copyright (c) Darkmintis

**Note**: The AI code generation engine and related proprietary features are maintained in a separate private repository and are not covered by this license.

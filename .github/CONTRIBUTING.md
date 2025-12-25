# Contributing to Koda

Thank you for your interest in contributing to Koda! We welcome contributions from everyone. This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Commit Guidelines](#commit-guidelines)
- [Testing](#testing)
- [Documentation](#documentation)

## Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to conduct@koda.design.

## Getting Started

### Prerequisites

- **Node.js** (version 18 or higher)
- **Docker** and **Docker Compose**
- **Git**
- **Clojure** (for backend development)

### Development Setup

1. **Fork and Clone the Repository**
   ```bash
   git clone https://github.com/your-username/koda.git
   cd koda
   ```

2. **Install Dependencies**
   ```bash
   # Install frontend dependencies
   npm install

   # Install backend dependencies
   cd backend && clojure -P
   ```

3. **Set Up Development Environment**
   ```bash
   # Start the development environment
   docker-compose up -d
   ```

4. **Run the Application**
   ```bash
   # Start the development server
   npm run dev
   ```

## How to Contribute

### Types of Contributions

We welcome the following types of contributions:

- 🐛 **Bug Reports**: Report bugs using our [bug report template](.github/ISSUE_TEMPLATE/bug-report.yml)
- ✨ **Feature Requests**: Suggest new features using our [feature request template](.github/ISSUE_TEMPLATE/feature-request.yml)
- 🔧 **Code Contributions**: Fix bugs or implement new features
- 📚 **Documentation**: Improve documentation, tutorials, or examples
- 🧪 **Testing**: Write or improve tests
- 🎨 **Design**: UI/UX improvements and design contributions

### Finding Issues to Work On

- Check out our [GitHub Issues](https://github.com/Darkmintis/Koda/issues) page
- Look for issues labeled `good first issue` or `help wanted`
- Issues labeled `needs triage` may need more investigation

## Pull Request Process

1. **Create a Branch**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/issue-number-description
   ```

2. **Make Your Changes**
   - Write clear, concise commit messages
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed

3. **Test Your Changes**
   ```bash
   # Run tests
   npm test

   # Run linting
   npm run lint

   # Build the project
   npm run build
   ```

4. **Submit a Pull Request**
   - Fill out the [pull request template](PULL_REQUEST_TEMPLATE.md)
   - Reference any related issues
   - Ensure CI checks pass
   - Request review from maintainers

5. **Address Review Feedback**
   - Make requested changes
   - Push updates to your branch
   - Maintain open communication with reviewers

## Commit Guidelines

We follow the [Conventional Commits](https://conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Commit Types

- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests or correcting existing tests
- **build**: Changes that affect the build system or external dependencies
- **ci**: Changes to our CI configuration files and scripts
- **chore**: Other changes that don't modify src or test files

### Examples

```
feat: add dark mode toggle
fix: resolve memory leak in canvas rendering
docs: update API documentation
refactor: simplify component architecture
```

## Testing

### Running Tests

```bash
# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Run specific test file
npm test -- path/to/test/file.test.js

# Run integration tests
npm run test:e2e
```

### Writing Tests

- Write tests for all new features and bug fixes
- Use descriptive test names that explain the expected behavior
- Follow the existing testing patterns in the codebase
- Aim for good test coverage

### Test Coverage

We aim to maintain high test coverage. Please ensure your changes don't significantly reduce coverage.

## Documentation

### Code Documentation

- Add JSDoc/TSDoc comments for new functions and components
- Keep comments up to date when modifying existing code
- Use clear, descriptive variable and function names

### Project Documentation

- Update README.md for significant changes
- Add examples for new features
- Keep API documentation current
- Update CHANGELOG.md for user-facing changes

## Development Workflow

### Branch Strategy

- `main`: Production-ready code and active development
- `feature/*`: Feature branches (merge directly to main)
- `fix/*`: Bug fix branches (merge directly to main)

### Code Review Process

1. **Automated Checks**: CI runs linting, tests, and builds
2. **Peer Review**: At least one maintainer reviews the code
3. **Approval**: Code must be approved before merging
4. **Merge**: Squash merge with descriptive commit message

### Release Process

1. Features are merged directly into `main`
2. Releases are tagged from `main` using semantic versioning
3. Release notes are generated from commit messages
4. CI/CD automatically builds and deploys on tagged releases

## Getting Help

- **Documentation**: Check our [docs](https://docs.koda.design)
- **Discussions**: Use [GitHub Discussions](https://github.com/Darkmintis/Koda/discussions) for questions
- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/Darkmintis/Koda/issues)
- **Community**: Join our [Discord community](https://discord.gg/koda-design)

## Recognition

Contributors are recognized in our [CONTRIBUTORS.md](../CONTRIBUTORS.md) file. We also highlight significant contributions in our release notes.

Thank you for contributing to Koda! 🎉

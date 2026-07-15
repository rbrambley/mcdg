# MCDG Website

Official website for the MCDG (Minecraft Disc Golf) modpack - User Guide and Documentation.

## Overview

This is an Astro-based static site built with GitHub Pages deployment. It serves as both a comprehensive user guide and a promotional showcase for the MCDG modpack.

## Tech Stack

- **Framework:** Astro 4.x
- **Styling:** Tailwind CSS + Custom CSS
- **Deployment:** GitHub Pages
- **Language:** TypeScript

## Features

- **Comprehensive User Guide:** Installation, gameplay, progression, and multiplayer documentation
- **Interactive Tools:** Disc flight calculator, tier comparison, course planner
- **Feature Documentation:** Detailed explanations of physics, courses, wind system, enchantments
- **Admin Resources:** Server setup, permissions, course management guides
- **Community Section:** Showcase, FAQ, contribution guidelines
- **Responsive Design:** Mobile-optimized with touch-friendly interactions

## Getting Started

### Prerequisites

- Node.js 20+
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Project Structure

```
docs/
├── src/
│   ├── pages/              # Page routes
│   │   ├── guide/         # User guide pages
│   │   ├── features/      # Feature documentation
│   │   ├── admin/         # Admin resources
│   │   ├── progression/   # Progression guides
│   │   ├── reference/     # Technical references
│   │   └── community/     # Community pages
│   ├── components/        # Reusable components
│   │   ├── layout/        # Layout components
│   │   ├── ui/           # UI components
│   │   ├── content/      # Content components
│   │   └── search/       # Search components
│   ├── layouts/          # Page layouts
│   └── styles/           # Global styles
├── public/               # Static assets
└── .github/             # GitHub Actions workflows
```

## Development

### Adding New Pages

1. Create a new `.astro` file in the appropriate `src/pages/` directory
2. Use the appropriate layout (MainLayout, GuideLayout, FeatureLayout, etc.)
3. Follow the existing content structure and styling conventions

### Adding New Components

1. Create a new `.astro` file in `src/components/`
2. Use TypeScript interfaces for props
3. Include scoped styles in the `<style>` block
4. Add interactive scripts in the `<script>` block

### Content Updates

Content is primarily migrated from the main MCDG project documentation. When updating:

1. Update the source markdown files in the main project
2. Sync changes to the corresponding Astro pages
3. Test the build locally before committing

## Deployment

The site is automatically deployed to GitHub Pages via GitHub Actions when pushing to the `master` or `main` branch.

### Manual Deployment

```bash
# Build the site
npm run build

# The output will be in the `dist/` directory
# Deploy the contents of `dist/` to your hosting provider
```

## Configuration

### Astro Config

Located in `astro.config.mjs`:
- Site URL and base path
- Integration settings (MDX, Tailwind, Sitemap)
- Build configuration

### Tailwind Config

Located in `tailwind.config.mjs`:
- Theme colors and variables
- Custom utilities
- Plugin configuration

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test locally with `npm run dev`
5. Submit a pull request

## License

This website is part of the MCDG project. All rights reserved.

## Support

For issues or questions about the website:
- Open an issue on GitHub
- Contact the MCDG development team
- Check the [Community FAQ](https://rbrambley.github.io/mcdg/community/faq)

## Links

- **Main Project:** https://github.com/rbrambley/mcdg
- **Live Site:** https://rbrambley.github.io/mcdg
- **Documentation:** https://rbrambley.github.io/mcdg/guide/installation

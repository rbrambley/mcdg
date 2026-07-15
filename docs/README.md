# MCDG Website

This is the Astro-based website and user guide for the MCDG Minecraft Disc Golf modpack.

## URLs

- **Local development:** `http://localhost:4321/mcdg/`
- **Production (GitHub Pages):** `https://rbrambley.github.io/mcdg/`

## Development

```bash
cd docs
npm install
npm run dev
```

The site uses `base: '/mcdg/'` so that links work identically in development and when deployed to the `rbrambley/mcdg` GitHub Pages project site.

## Build

```bash
cd docs
npm run build
```

The static output is written to `docs/dist/`. GitHub Actions runs this automatically on every push to `master` (or `main`) and deploys the result to GitHub Pages.

### Deploying for the first time

1. Commit and push the `docs/` folder and `.github/workflows/deploy.yml` to the default branch.
2. In the repository settings, go to **Pages → Source** and select **GitHub Actions**.
3. Push any change to trigger the workflow, or run it manually from the Actions tab.

If Pages is left on **Deploy from a branch**, the repository `README.md` will be rendered instead of the Astro site.

## Structure

- `src/pages/` — Astro pages for guides, features, admin docs, progression, reference, and community content.
- `src/components/` — Reusable UI, layout, content, and search components.
- `src/layouts/` — Page layouts.
- `src/styles/` — Global styles, themes, and component styles.
- `public/` — Static assets such as screenshots, textures, diagrams, and videos.
- `astro.config.mjs` — Site config with the correct GitHub Pages `site` and `base` values.

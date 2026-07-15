import { defineConfig } from 'astro/config';
import mdx from '@astrojs/mdx';
import tailwind from '@astrojs/tailwind';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://rbrambley.github.io',
  base: '/mcdg',
  integrations: [
    mdx(),
    tailwind({
      applyBaseStyles: false,
    }),
    sitemap(),
  ],
  build: {
    format: 'directory',
  },
});

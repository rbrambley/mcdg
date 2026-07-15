import { defineConfig } from 'astro/config';

// https://astro.build/config
export default defineConfig({
  site: 'https://rbrambley.github.io',
  base: '/mcdg/',
  outDir: './dist',
  trailingSlash: 'ignore',
});

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,svelte,ts,tsx,vue}'],
  theme: {
    extend: {
      colors: {
        background: '#0f0f1a',
        accent: '#00ff88',
        tier: {
          training: '#b87333',
          wooden: '#8b7355',
          stone: '#7a7a7a',
          iron: '#a0a0a0',
          gold: '#ffd700',
          diamond: '#00ffff',
          netherite: '#1a1a2e',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['Fira Code', 'monospace'],
      },
    },
  },
  plugins: [],
};

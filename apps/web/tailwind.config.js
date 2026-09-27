/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}'
  ],
  theme: {
    extend: {
      fontFamily: {
        display: ['var(--font-comic-display)', 'system-ui', 'sans-serif'],
        sans: ['var(--font-comic-body)', 'system-ui', 'sans-serif']
      },
      colors: {
        comic: {
          red: 'var(--color-primary)',
          'red-hover': 'var(--color-primary-hover)',
          'red-light': 'var(--color-primary-light)',
          ink: 'var(--color-ink)',
          muted: 'var(--color-ink-muted)',
          faint: 'var(--color-ink-faint)',
          paper: 'var(--color-bg)',
          panel: 'var(--color-surface)',
          yellow: 'var(--color-accent-yellow)',
          blue: 'var(--color-accent-blue)'
        }
      },
      boxShadow: {
        comic: '4px 4px 0px 0px #18181b',
        'comic-sm': '2px 2px 0px 0px #18181b',
        'comic-lg': '6px 6px 0px 0px #18181b',
        'comic-red': '4px 4px 0px 0px #e11d48'
      }
    }
  },
  plugins: []
};

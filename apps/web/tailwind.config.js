/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}'
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#0D6EFD',
          light: '#E7F0FE',
          dark: '#004785'
        },
        secondary: {
          DEFAULT: '#00A86B',
          light: '#D1F4E4'
        },
        tertiary: '#FF7A00',
        charcoal: '#111315',
        surface: '#FFFFFF',
        surfaceDark: '#1A1C1E',
        background: '#F8F9FA'
      }
    }
  },
  plugins: []
};

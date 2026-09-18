/** @type {import('next').NextConfig} */
const nextConfig = {
  transpilePackages: [
    '@trippin/shared-types',
    '@trippin/itinerary-schema',
    '@trippin/api-contracts'
  ],
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'images.unsplash.com'
      }
    ]
  }
};

module.exports = nextConfig;

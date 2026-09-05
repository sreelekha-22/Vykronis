/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  env: {
    VYKRONIS_API_URL: process.env.VYKRONIS_API_URL ?? 'http://localhost:8080',
  },
};

export default nextConfig;
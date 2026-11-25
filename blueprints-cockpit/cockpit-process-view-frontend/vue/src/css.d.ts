// The build loads a .css file as text, see the loader in scripts/build-micro-frontends.mjs. So an
// import of a .css file gives a string, which src/pages/draw-process.ts puts into a shadow root.
declare module '*.css' {
  const css: string;
  export default css;
}

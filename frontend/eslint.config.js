// @ts-check
// ESLint con angular-eslint (docs/decisiones.md → «ESLint»): `npm run lint`, y en el CI en cada PR.
const eslint = require("@eslint/js");
const { defineConfig, globalIgnores } = require("eslint/config");
const tseslint = require("typescript-eslint");
const angular = require("angular-eslint");

module.exports = defineConfig([
  // Lo que se genera: la compilación, la caché de Angular, la cobertura y los informes de Playwright
  globalIgnores(["dist/", ".angular/", "coverage/", "playwright-report/", "test-results/"]),
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      "@angular-eslint/directive-selector": [
        "error",
        {
          type: "attribute",
          prefix: "app",
          style: "camelCase",
        },
      ],
      "@angular-eslint/component-selector": [
        "error",
        {
          type: "element",
          prefix: "app",
          style: "kebab-case",
        },
      ],
      // Un _ al frente dice «sin usar a propósito»: el parámetro de una firma y lo que se saca de un objeto
      // con { [id]: _, ...resto }
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", ignoreRestSiblings: true },
      ],
    },
  },
  {
    // En las pruebas, un simulado que no hace nada es justamente () => {}
    files: ["**/*.spec.ts"],
    rules: {
      "@typescript-eslint/no-empty-function": "off",
    },
  },
  {
    files: ["**/*.html"],
    extends: [
      angular.configs.templateRecommended,
      angular.configs.templateAccessibility,
    ],
    rules: {
      // <button appVerContrasena> trae su ícono y su aria-label del componente (shared/formularios/ver-contrasena.ts)
      "@angular-eslint/template/elements-content": ["error", { allowList: ["appVerContrasena"] }],
    },
  },
]);

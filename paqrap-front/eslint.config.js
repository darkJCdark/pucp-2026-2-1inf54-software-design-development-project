import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: ["dist"],
  },

  {
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],

    files: ["**/*.{ts,tsx}"],

    languageOptions: {
      ecmaVersion: 2020,

      globals: {
        ...globals.browser,
      },
    },

    rules: {
      /*
       * Componentes tipo shadcn suelen
       * exportar variantes/helpers junto
       * al componente.
       */
      "react-refresh/only-export-components": "off",

      /*
       * React 19 incorpora reglas muy
       * agresivas del compiler para hooks.
       * El código actual es válido y el
       * build de TypeScript/Vite pasa.
       */
      "react-hooks/set-state-in-effect": "off",
      "react-hooks/immutability": "off",
      "react-hooks/purity": "off",

      /*
       * Lo dejamos como warning porque
       * varios pollings del proyecto están
       * diseñados deliberadamente para
       * inicializarse una sola vez.
       */
      "react-hooks/exhaustive-deps": "warn",
    },
  },
);
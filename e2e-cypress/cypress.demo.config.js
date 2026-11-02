// Config SO para a demo de evidencias (videos e prints). Nao e usada na CI.
//
// Reaproveita toda a config principal (baseUrl, env, tasks, Allure) e so liga a
// gravacao. As evidencias vao para cypress/evidencias-demo/ (no .gitignore) e NAO
// sao apagadas entre execucoes, entao sobrevivem a um `npm run cy:run` depois.
//
// Por que um arquivo separado: o cypress.config.js define `video: false` dentro do
// bloco `e2e`, e o `--config video=true` da linha de comando nao sobrescreve um
// valor desse bloco (foi o que aconteceu na primeira tentativa: rodou sem video).
//
// Uso:  npm run demo         -> UI-01 + UI-02 passando  -> evidencias-demo/sucesso/
//       npm run demo:falha   -> UI-01 com conta inexistente -> evidencias-demo/falha/
//       (acrescente `-- --headed` para ver o navegador enquanto roda)
const { defineConfig } = require('cypress');
const base = require('./cypress.config.js');

// O npm informa qual script esta rodando; cada um grava na sua pasta, para o video
// do UI-01 da falha nao sobrescrever o video do UI-01 que passou.
const pasta = process.env.npm_lifecycle_event === 'demo:falha' ? 'falha' : 'sucesso';

module.exports = defineConfig({
  ...base,
  videosFolder: `cypress/evidencias-demo/${pasta}/videos`,
  screenshotsFolder: `cypress/evidencias-demo/${pasta}/screenshots`,
  trashAssetsBeforeRuns: false,
  e2e: {
    ...base.e2e,
    video: true,
  },
});

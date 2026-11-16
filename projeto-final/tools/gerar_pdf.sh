#!/usr/bin/env bash
# Gera diagramas, HTML e PDF da 1ª entrega. Requer python3, pandoc e Playwright (Node).
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/gerar_diagramas.py
pandoc entrega-1.md -s --metadata lang=pt-BR -c tools/estilo.css -o entrega-1.html
node -e "
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const p=await b.newPage();
await p.goto('file://'+process.cwd()+'/entrega-1.html',{waitUntil:'networkidle'});
await p.pdf({path:'entrega-1.pdf',format:'A4',printBackground:true,displayHeaderFooter:true,
headerTemplate:'<span></span>',
footerTemplate:'<div style=\"font-size:8px;width:100%;text-align:center;color:#777\">VelaPay — 1ª Entrega · <span class=pageNumber></span>/<span class=totalPages></span></div>',
margin:{top:'18mm',bottom:'18mm',left:'16mm',right:'16mm'}});await b.close();})();"
rm entrega-1.html
echo "PDF gerado: $(pwd)/entrega-1.pdf"

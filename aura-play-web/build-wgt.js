/**
 * Aura Play - Tizen WGT Packager
 * Packages config.xml, index.html, icon.png, css, js, and assets into AuraPlay.wgt
 */
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const projectDir = __dirname;
const outputWgt = path.join(projectDir, 'AuraPlay.wgt');
const tempZip = path.join(projectDir, 'temp_tizen.zip');

console.log('🚀 Packaging Aura Play for Samsung Tizen OS...');

try {
    // 1. Remove previous build artifacts
    if (fs.existsSync(outputWgt)) fs.unlinkSync(outputWgt);
    if (fs.existsSync(tempZip)) fs.unlinkSync(tempZip);

    // 2. Files and folders to include
    const includes = ['config.xml', 'index.html', 'icon.png', 'css', 'js', 'assets'];
    const includeArgs = includes.map(f => `'${path.join(projectDir, f)}'`).join(', ');

    // 3. Compress using PowerShell Compress-Archive
    const psCmd = `powershell -Command "Compress-Archive -Path ${includeArgs} -DestinationPath '${tempZip}' -Force"`;
    console.log('📦 Compressing web widget assets...');
    execSync(psCmd, { stdio: 'inherit' });

    // 4. Rename .zip to .wgt (Tizen Web Widget format)
    fs.renameSync(tempZip, outputWgt);

    const stats = fs.statSync(outputWgt);
    const sizeKb = (stats.size / 1024).toFixed(1);
    console.log(`\n✅ Successfully generated Tizen Widget Package:`);
    console.log(`   📁 File: ${outputWgt}`);
    console.log(`   📊 Size: ${sizeKb} KB`);
    console.log(`\n🎉 Ready to install on Samsung Smart TV via Tizen Studio or 'tizen install'!\n`);
} catch (err) {
    console.error('❌ Failed to package AuraPlay.wgt:', err);
    process.exit(1);
}

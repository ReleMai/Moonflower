// =============================================================================
// Haven Cartographer - First-time Setup
// =============================================================================

const fs = require('fs');
const path = require('path');

const dirs = [
    'data',
    'data/tiles',
    'data/maps',
    'data/exports',
    'data/live',
    'data/screenshots'
];

console.log('Haven Cartographer — Setup');
console.log('='.repeat(40));

for (const dir of dirs) {
    const fullPath = path.resolve(__dirname, '..', dir);
    if (!fs.existsSync(fullPath)) {
        fs.mkdirSync(fullPath, { recursive: true });
        console.log(`  Created: ${dir}/`);
    } else {
        console.log(`  Exists:  ${dir}/`);
    }
}

console.log('\nSetup complete. Run "npm start" to launch the server.');

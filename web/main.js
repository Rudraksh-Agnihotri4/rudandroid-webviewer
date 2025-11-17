"use strict";
// Get references to our HTML elements
const canvas = document.getElementById('frameCanvas');
const statsOverlay = document.getElementById('statsOverlay');
const ctx = canvas.getContext('2d');
if (ctx && statsOverlay) {
    // 1. Create a new image object
    const img = new Image();
    // 2. Set the source to our sample image
    img.src = 'sample_frame.jpeg';
    // 3. When the image loads, draw it onto the canvas
    img.onload = () => {
        // Set canvas size to match image
        canvas.width = img.width;
        canvas.height = img.height;
        // Draw the image
        ctx.drawImage(img, 0, 0);
        // 4. Update the stats overlay [cite: 39]
        const fps = 30; // Just a dummy value
        const resolution = `${img.width}x${img.height}`;
        statsOverlay.innerHTML = `
            <p><strong>Resolution:</strong> ${resolution}</p>
            <p><strong>Mock FPS:</strong> ${fps}</p>
        `;
    };
    img.onerror = () => {
        console.error("Could not load sample_frame.jpeg");
        statsOverlay.innerHTML = "<p>Error: Could not load sample_frame.jpeg</p>";
    };
}
else {
    console.error("Could not find canvas or stats element");
}

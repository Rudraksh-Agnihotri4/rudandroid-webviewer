const canvas = document.getElementById('frameCanvas') as HTMLCanvasElement;
const statsOverlay = document.getElementById('statsOverlay');
const ctx = canvas.getContext('2d');

if (ctx && statsOverlay) {
    const img = new Image();

    img.src = 'sample_frame.jpeg';

    img.onload = () => {
        canvas.width = img.width;
        canvas.height = img.height;

        ctx.drawImage(img, 0, 0);

        const fps = 30; 
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

} else {
    console.error("Could not find canvas or stats element");
}
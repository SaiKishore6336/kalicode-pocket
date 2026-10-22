const count = document.getElementById("count");
const hint = document.getElementById("hint");
const button = document.getElementById("tap");

let taps = 0;

button.addEventListener("click", () => {
  taps += 1;
  count.textContent = String(taps);
  hint.textContent = taps === 1
    ? "One tap. The preview is live."
    : `${taps} taps. Still fast.`;
  console.log("KaliCode Pocket tap:", taps);
});

console.log("KaliCode Pocket preview ready");

// A place to put a little javascript if you need it.
window.platypubTimestamp = function (value) {
  const date = new Date(value);
  const parts = new Intl.DateTimeFormat('en-US', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: true,
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || '';
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')} ${get('dayPeriod')}`;
};

window.platypubCopyEmbed = async function (button) {
  const code = document.getElementById('embed-code').value;
  await navigator.clipboard.writeText(code);
  button.textContent = 'Copied';
  button.classList.remove('text-primary', 'hover:underline');
  button.classList.add('text-text');
  button.disabled = true;
  window.setTimeout(() => {
    button.textContent = 'Copy';
    button.classList.remove('text-text');
    button.classList.add('text-primary', 'hover:underline');
    button.disabled = false;
  }, 1000);
};

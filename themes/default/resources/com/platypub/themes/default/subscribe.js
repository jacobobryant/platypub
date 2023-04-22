const qs = require('querystring');
const axios = require('axios').default;
const config = require('./config.json');

async function verify_recaptcha(response) {
  return (await axios.post(
    "https://www.google.com/recaptcha/api/siteverify",
    qs.stringify({
      secret: config.recaptchaSecret,
      response: response,
    }))).data.success;
}

async function verify_address(email) {
  if (!email.includes("@")) {
    return false;
  }
  return true;
}

function send_welcome(email) {
  return axios.post(
    "https://api.mailgun.net/v3/" + config.mailgunDomain + "/messages",
    qs.stringify({...config.welcomeEmail, to: email}),
    {auth: {username: "api", password: config.mailgunKey}}
  );
}

function add_to_list(email, vars) {
  return axios.post(
    "https://api.mailgun.net/v3/lists/" + config.listAddress + "/members",
    qs.stringify({address: email, upsert: true, vars: JSON.stringify(vars)}),
    {auth: {username: "api", password: config.mailgunKey}}
  );
}

exports.handler = async function (event, context) {
  const params = qs.parse(event.body);
  const email = (params.email || "").toLowerCase().trim();
  let error;
  if (!await verify_recaptcha(params['g-recaptcha-response'])) {
    error = 'recaptcha-failed';
  } else if (!await verify_address(email)) {
    error = 'invalid-email';
  } else {
    try {
      const {href, referrer} = params;
      const vars = {href, referrer, joinedAt: new Date()};
      await send_welcome(email);
      await add_to_list(email, vars);
    } catch (e) {
      console.log(e.stack);
      error = 'unknown';
    }
  }
  let url;
  if (error) {
    try {
      url = new URL(params.href);
    } catch (e) {
      url = new URL(config.siteUrl);
    }
    url.searchParams.set('error', error);
    url.hash = "recaptcha-form";
  } else {
    url = new URL(config.subscribeRedirect);
  }
  url.searchParams.set('email', params.email);
  return {
    statusCode: 303,
    headers: {location: url.href},
  };
};

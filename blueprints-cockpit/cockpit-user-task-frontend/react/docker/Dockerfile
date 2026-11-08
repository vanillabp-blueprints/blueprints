# The micro-frontends of the workflow module in an nginx, for an application which takes them
# from here instead of from the UI jar. Build it with `npm run build:image` after `npm run build`.
FROM nginx:1.30.6-alpine

ARG WORKFLOW_MODULE_ID=loan-approval

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
# The same path as in the UI jar, so /loan-approval/bc-manifest.json works with both.
COPY dist/micro-frontends /usr/share/nginx/html/${WORKFLOW_MODULE_ID}

# syntax=docker/dockerfile:1

FROM node:20-alpine AS node_build

WORKDIR /app

COPY package* ./

RUN npm install

COPY tailwind.config.js input.css ./
COPY src/ ./src/

RUN npx tailwindcss -i input.css -o output.css --minify

FROM clojure:tools-deps

WORKDIR /app

COPY . ./
COPY --from=node_build /app/output.css ./resources/public/css/compiled/output.css
COPY --from=node_build /app/node_modules/ ./node_modules/

# maybe run npx shadow-cljs release here?
# rn, it's in build.clj.

RUN clj -T:build uber

CMD ["java", "-jar", "target/standalone.jar"]

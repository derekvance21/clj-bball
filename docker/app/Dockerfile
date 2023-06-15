# syntax=docker/dockerfile:1

FROM clojure:tools-deps

RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash
RUN apt-get install -y nodejs

WORKDIR /app

COPY build.clj deps.edn shadow-cljs.edn package* tailwind.config.js input.css ./
COPY src src/
COPY resources/public/index.html resources/public/index.html

RUN npm install

RUN npx tailwindcss -i input.css -o resources/public/css/compiled/output.css --minify

RUN npx shadow-cljs release app

RUN clj -T:build uber

CMD ["java", "-jar", "target/standalone.jar"]

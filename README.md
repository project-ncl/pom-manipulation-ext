# GH-Pages Branch

This branch contains the github pages content for https://github.com/project-ncl/pom-manipulation-ext and the pages are hosted at https://project-ncl.github.io/pom-manipulation-ext

The site uses Jekyll with the `jekyll-theme-slate` gem and a small override in `assets/css/style.scss`.

To preview changes locally:

```bash
bundle install --gemfile Gemfile.local
BUNDLE_GEMFILE=Gemfile.local bundle exec jekyll serve --watch -V
```

`Gemfile` mirrors the GitHub Pages build (`github-pages` gem). `Gemfile.local` is for local preview on newer Rubies, where the `github-pages` gem cannot be installed.

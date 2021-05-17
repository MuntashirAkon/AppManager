# SPDX-License-Identifier: GPL-3.0-or-later
html:
	# Docs: https://mirror.las.iastate.edu/tex-archive/support/make4ht/make4ht-doc.pdf
	# tex4ht: 2 = Divide into chapters, fn-in = Footnote in the same file, sec-filename = filename based on section name, sections+ = Links to TOC
	make4ht -su -c main.cfg -d ../en -f html5+tidy+common_domfilters main "fn-in, sec-filename, sections+"
	! [ -d ../images ] || rm -rf ../images; mv ../en/images ../images
	find ../images -depth 1 -exec sh -c 'f="{}"; mv $$f "$${f//-}" ' \;
	sed -i -e 's|src=\"\(\./\)\?\([^\"]\+\)\"|src=\"../\2\"|' \
		-e 's|src=\"\([^-]\+\)-\.\([^\"]\+\)\"|src=\"\1.\2\"|' \
		-e 's|\(<p[^>]\+>\)figure.*\(<img.*\)|\1\2|' \
		-e 's|<p class=\"noindent\"></p>||' ../en/main.html
	# https://github.com/tdewolff/minify/tree/master/cmd/minify
	#minify ../en/main.html -o ../en/index.html; rm ../en/main.html
	mv ../en/main.html ../en/index.html
	minify ../en/main.css -o ../en/main.min.css; rm ../en/main.css; mv ../en/main.min.css ../en/main.css

clean:
	rm main.html
	rm main.css
	rm images/*-.png

clean-all: clean
	rm -rf ../en
	rm -rf ../images

minify-custom-css:
	minify custom.css -o ../css/custom.css

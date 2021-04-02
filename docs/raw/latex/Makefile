html:
	# Docs: https://mirror.las.iastate.edu/tex-archive/support/make4ht/make4ht-doc.pdf
	# tex4ht: 2 = Divide into chapters, fn-in = Footnote in the same file, sec-filename = filename based on section name, sections+ = Links to TOC
	make4ht -su -c main.cfg -d ../en -f html5+tidy+common_domfilters main "fn-in, sec-filename, sections+"; \
	mv ../en/main.html ../en/index.html
	cp custom.css ../en/

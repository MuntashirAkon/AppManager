html:
	# Docs: https://mirror.las.iastate.edu/tex-archive/support/make4ht/make4ht-doc.pdf
	# tex4ht: 2 = Divide into chapters, fn-in = Footnote in the same file, sec-filename = filename based on section name, sections+ = Links to TOC
	make4ht -su -c main.cfg -f html5+tidy+common_domfilters main "fn-in, sec-filename, sections+"; \
	mv ./main.html ./index.html

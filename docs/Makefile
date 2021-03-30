LOCALE = en
epub:
	cd ./$(LOCALE)/; \
	tex4ebook -s -f epub3 ./main.tex

html:
	# Docs: https://mirror.las.iastate.edu/tex-archive/support/make4ht/make4ht-doc.pdf
	# tex4ht: 2 = Divide into chapters, fn-in = Footnote in the same file, sec-filename = filename based on section name, sections+ = Links to TOC
	cd ./$(LOCALE)/; \
	make4ht -su -c ../main.cfg -f html5+tidy+common_domfilters main "fn-in, sec-filename, sections+"; \
	cp ../custom.css ./; \
	mv ./main.html ./index.html

pdf:
	cd ./$(LOCALE)/; \
	pdflatex -file-line-error -interaction=nonstopmode -synctex=1 -output-directory=. -shell-escape main.tex

gettext:
	cd ./en/; \
	po4a-gettextize -f latex -M UTF-8 -m main.tex -p ../po_files/main.pot --copyright-holder "Muntashir Al-Islam" \
	 --package-name "io.github.muntashirakon.AppManager" --package-version \
	 `cat ../../app/build.gradle | grep -m1 versionName | awk -F \" '{print "v"$$2}'`

gettext_po:
	cd ./en/; \
	po4a-gettextize -f latex -M UTF-8 -m main.tex -p ../po_files/main.$(LOCALE).po --copyright-holder "Muntashir Al-Islam" \
	 --package-name "io.github.muntashirakon.AppManager" --package-version \
	 `cat ../../app/build.gradle | grep -m1 versionName | awk -F \" '{print "v"$$2}'`

translate:
	[[ -d $(LOCALE) ]] || mkdir $(LOCALE)
	cd ./en/; \
    cp -a images ../$(LOCALE); \
	po4a-translate -f latex -m main.tex -p ../po_files/main.$(LOCALE).po -l ../$(LOCALE)/main.tex --keep 0

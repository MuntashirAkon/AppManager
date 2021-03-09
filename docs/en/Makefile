epub:
	tex4ebook -xs -f epub3 main.tex

html_pandoc:
	pandoc --pdf-engine=xelatex --variable mainfont="Palatino" --variable sansfont="Helvetica" --variable monofont="Menlo" \
	 -s -f latex --toc -o test.html main.tex --metadata title="App Manager" --template=toc-sidebar.html -B navbar.html

html:
	# Docs: https://mirror.las.iastate.edu/tex-archive/support/make4ht/make4ht-doc.pdf
	make4ht -sxu -c main.cfg -f html5+tidy+common_domfilters main "fn-in"
	sed -i -e 's|<p class='\''noindent'\''></p>||g' main.html
	cat custom.css main.css >> main.css.tmp
	rm main.css
	mv main.css.tmp main.css

pdf:
	xelatex -file-line-error -interaction=nonstopmode -synctex=1 -output-directory=. -shell-escape main.tex

gettext:
	po4a-gettextize -f latex -m main.tex -p ../po_files/main.pot --copyright-holder "Muntashir Al-Islam" \
	 --package-name "io.github.muntashirakon.AppManager" --package-version \
	 `cat ../../app/build.gradle | grep -m1 versionName | awk -F \" '{print "v"$$2}'`

gettext_po:
	po4a-gettextize -f latex -m main.tex -p ../po_files/main.$(LOCALE).po --copyright-holder "Muntashir Al-Islam" \
	 --package-name "io.github.muntashirakon.AppManager" --package-version \
	 `cat ../../app/build.gradle | grep -m1 versionName | awk -F \" '{print "v"$$2}'`

translate:
	po4a-translate -f latex -m main.tex -p ../po_files/main.$(LOCALE).po -l main.$(LOCALE).tex

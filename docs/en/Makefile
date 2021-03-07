epub:
	tex4ebook -x -f epub3 -s main

html_pandoc:
	pandoc --pdf-engine=xelatex --variable mainfont="Palatino" --variable sansfont="Helvetica" --variable monofont="Menlo" -s -f latex --toc -o test.html main.tex --metadata title="App Manager" --template=toc-sidebar.html -B navbar.html

html:
	make4ht -sxu -f html5+tidy+common_domfilters main.tex
	sed -i -e 's|<p class='\''noindent'\''></p>||g' main.html
	cat custom.css main.css >> main.css.tmp
	rm main.css
	mv main.css.tmp main.css

pdf:
	xelatex -file-line-error -interaction=nonstopmode -synctex=1 -output-directory=. -shell-escape main.tex

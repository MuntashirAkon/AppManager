#!/bin/bash

#function func_build-html {

OUTPUT=main.pandoc.html
pandoc main.tex -c main.css -c custom.css -o $OUTPUT -t html5 -f latex -s --toc -N --section-divs --default-image-extension=png -i --verbose

##Custom Color Fixup
while read line
do
colorset=$(echo $line | sed -e "s/\\definecolor{//" -e "s/}{HTML}{/ /" -e "s/}//")
colorname=$(echo $colorset | awk '{print $1}')
colorcode=$(echo $colorset | awk '{print $2}' | sed -e "s/^/#/")
#echo N $colorname C $colorcode
sed -i -e "s/style\=\"background-color\: ${colorname}\"/style\=\"background-color\: ${colorcode}\"/g" \
       -e "s/style\=\"color\: ${colorname}\"/style\=\"color\: ${colorcode}\"/g" $OUTPUT
done < <(grep "\definecolor" main.tex)


##Add link on section title
while read line
do
sectionid=$(echo $line |  grep -o -P "(?<=href\=\"#).*" | grep -o -P ".*(?=\"\>\<span)")
done < <(grep -o "<a href=\"#.*\"><span class=\"toc-section-number\">" $OUTPUT)


##Icon Fixup(Need --default-image-extension=png option)
sed -i -e "s/\<img src\=\"images\/icon.png\" style\=\"width:2cm\"/\<img src\=\"images\/icon-.png\" style\=\"width\:16.69\%\"/g" $OUTPUT

##Hide Level5 section number
sed -i -e "s/<span class=\"header-section-number\">.*\..*\..*\..*\..*<\/span\>/<span class=\"header-section-number\" style=\"display\: none\;\"><\/span>/g" $OUTPUT

##Alertbox Fixup
sed -i -r "s/<div class\=\"amalert--(.*)\">/<div class\=\"amalert\" style\=\"border\: 3.0pt solid #\1\;\">/g" $OUTPUT


#}

#function func_update-xliff {
# Working
#}

#function func_merge-translation {
# Working
#}

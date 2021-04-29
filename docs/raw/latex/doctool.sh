#!/bin/bash

#function func_build-html {

OUTPUT=main.pandoc.html
pandoc main.tex -c main.css -c custom.css -o $OUTPUT -t html5 -f latex -s --toc -N --section-divs --default-image-extension=png -i --listings --verbose

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

##Alertbox Fixup
while read line
do
sed -i -e "s/--/\" style\=\"border\: 3.0pt solid #/g" $OUTPUT
done < <(grep "<div class=\"amalert--.*;\">" $OUTPUT)

##Icon Fixup(Need --default-image-extension=png option)
sed -i -e "s/\<img src\=\"images\/icon.png\" style\=\"width:2cm\"/\<img src\=\"images\/icon-.png\" style\=\"width\:16.69\%\"/g" $OUTPUT

##Hide Level5 section number
sed -i -e "s/<span class=\"header-section-number\">.*\..*\..*\..*\..*<\/span\>/<span class=\"header-section-number\" style=\"display\: none\;\"><\/span>/g" $OUTPUT

#}

#function func_update-xliff {
# Working
#}

#function func_merge-translation {
# Working
#}

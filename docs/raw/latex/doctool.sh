#!/bin/bash

function func_help {
echo -n "\
./doctool.sh COMMAND ARGS
--COMMANDS--
buildhtml [OUTPUTFILE(.html)]
Build HTML from TeX

updatetranslation [OUTPUTFILE(.xliff)]
Extract strings and create xliff translation file
NOTE:Use this option when you want to start new translation too

mergetranslation [INPUTFILE(.xliff)] [SOURCEDIR]
Merge translation from xliff to TeX

help
Show this help and exit
"
}

function func_build-html {

OUTPUT=${arg[$2]}
pandoc main.tex -c main.css -c custom.css -o $OUTPUT -t html5 -f latex -s --toc -N --section-divs --default-image-extension=png -i --verbose

##Custom Color Fixup
while read line
do
colorset=$(echo $line | sed -e "s/\\definecolor{//" -e "s/}{HTML}{/ /" -e "s/}//")
colorname=$(echo $colorset | awk '{print $1}')
colorcode=$(echo $colorset | awk '{print $2}' | sed -e "s/^/#/")
sed -i -e "s/style\=\"background-color\: ${colorname}\"/style\=\"background-color\: ${colorcode}\"/g" \
       -e "s/style\=\"color\: ${colorname}\"/style\=\"color\: ${colorcode}\"/g" $OUTPUT
done < <(grep "\definecolor" main.tex)


##Add link on section title
while read line
do
sectionid=$(echo $line | grep -oP "(?<=\<span class\=\"toc-section-number\"\>).*(?=<\/span\>)")
tocsectionid=toc:${sectionid}
sed -i -r "s/<span class=\"toc-section-number\">${sectionid}<\/span>/<span class\=\"toc-section-number\" id\=\"${tocsectionid}\"\>${sectionid}<\/span\>/g" $OUTPUT
sed -i -r "s/(<(.*) data-number=\"${sectionid}\"><span class=\"header-section-number\">${sectionid}<\/span>.*<\/\2>)/<a href=\"#${tocsectionid}\">\1<\/a>/" $OUTPUT
done < <(grep -o "<span class=\"toc-section-number\">.*<\/span>" $OUTPUT)


##Icon Fixup(Need --default-image-extension=png option)
sed -i -e "s/\<img src\=\"images\/icon.png\" style\=\"width:2cm\"/\<img src\=\"images\/icon-.png\" style\=\"width\:16.69\%\"/g" $OUTPUT

##Hide Level5 section number
sed -i -e "s/<span class=\"header-section-number\">.*\..*\..*\..*\..*<\/span\>/<span class=\"header-section-number\" style=\"display\: none\;\"><\/span>/g" $OUTPUT

##Alertbox Fixup
sed -i -r "s/<div class\=\"amalert--(.*)\">/<div class\=\"amalert\" style\=\"border\: 3.0pt solid #\1\;\">/g" $OUTPUT


}

function func_update-xliff {
echo update translation
}

function func_merge-translation {
echo merge translation
}

arg=("$@")
case ${arg[$1]} in
"help" ) func_help ;;
"buildhtml" ) func_build-html ;;
"updatetranslation" ) func_update-xliff ;;
"mergetranslation" ) func_merge-translation ;;
esac

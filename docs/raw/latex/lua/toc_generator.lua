_ENV = pandoc

local not_empty = function (x) return #x > 0 end
local section_to_toc_item

--[[
    Produces ToC items recursively in the following format:
    <ul ...>
        <li><span><span class="toc-section-number">1.1</span> <a id="1.1" href="#sec:terminologies">Terminologies</a></span></li>
        ...
    </ul>
]]--
local to_toc_item = function (number, text, id, subcontents)
    local toc_entry
    if number then
        -- <span class="toc-section-number">1.1</span>
        local section_number = Span(Str(number), {class='toc-section-number'})
        -- <a id="1.1" href="#sec:terminologies">Terminologies</a>
        local link = id == '' and text or Link(text, '#' .. id, '', {id='toc:' .. id})
        toc_entry = Span{section_number, Space(), link}
    else
        toc_entry = id == '' and text or Link(text, '#' .. id, {id='toc:' .. id})
    end
    local subitems = subcontents:map(section_to_toc_item):filter(not_empty)
    return List{Plain{toc_entry}} ..
    (#subitems == 0 and {} or {BulletList(subitems)})
end

section_to_toc_item = function (div)
    -- bail if this is not a section wrapper
    if div.t ~= 'Div' or not div.content[1] or div.content[1].t ~= 'Header' then
        return {}
    end
    local heading = div.content:remove(1)
    -- bail if the header level is greater than 3
    if heading.level > 3 then
        return {}
    end
    local number = heading.attributes.number
    -- bail if this is not supposed to be included in the toc
    if not number and heading.classes:includes 'unlisted' then
        return {}
    end

    return to_toc_item(number, heading.content, div.identifier, div.content)
end

-- return filter
return {
    {
    Pandoc = function (doc)
        -- avoid problems with headings nested below divs:
        local blocks_no_divs = doc.blocks:walk{
            Div = function (x) return x.content end
        }
        local sections = utils.make_sections(true, nil, blocks_no_divs)
        local toc_items = sections:map(section_to_toc_item):filter(not_empty)
        doc.meta['table-of-contents'] = {BulletList(toc_items)}
        doc.meta.toc = doc.meta['table-of-contents']
        return doc
    end
    },
}
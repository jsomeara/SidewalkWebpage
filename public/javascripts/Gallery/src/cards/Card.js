/**
 * A Card module.
 * @param params properties of the associated label.
 * @param imageUrl google maps static image url for label.
 * @param modal Modal object; used to update the expanded view when modifying a card.
 * @returns {Card}
 * @constructor
 */
function Card (params, imageUrl, modal) {
    let self = this;

    // UI card element.
    let card = null;

    let validationMenu = null;
    let imageId = null;

    // Properties of the label in the card.
    let properties = {
        label_id: undefined,
        label_type: undefined,
        gsv_panorama_id: undefined,
        image_capture_date: undefined,
        label_timestamp: undefined,
        heading: undefined,
        pitch: undefined,
        zoom: undefined,
        original_canvas_x: undefined,
        original_canvas_y: undefined,
        severity: undefined,
        temporary: undefined,
        description: undefined,
        street_edge_id: undefined,
        region_id: undefined,
        correct: undefined,
        val_counts: undefined,
        correctness: undefined,
        user_validation: undefined,
        tags: []
    };

    // Paths to label icon images.
    // TODO: This object should be moved to a util file since it is shared in validation and admin tools as well.
    let iconImagePaths = {
        CurbRamp : '/assets/images/icons/AdminTool_CurbRamp.png',
        NoCurbRamp : '/assets/images/icons/AdminTool_NoCurbRamp.png',
        Obstacle : '/assets/images/icons/AdminTool_Obstacle.png',
        SurfaceProblem : '/assets/images/icons/AdminTool_SurfaceProblem.png',
        Other : '/assets/images/icons/AdminTool_Other.png',
        Occlusion : '/assets/images/icons/AdminTool_Occlusion.png',
        NoSidewalk : '/assets/images/icons/AdminTool_NoSidewalk.png',
        Crosswalk : '/assets/images/icons/AdminTool_Crosswalk.png',
        Signal : '/assets/images/icons/AdminTool_Signal.png'
    };

    // Status to determine if static imagery has been loaded.
    let status = {
        imageFetched: false
    };

    // The label icon to be placed on the static pano image.
    const labelIcon = new Image();
    self.labelIcon = labelIcon;

    // The static pano image.
    const panoImage = new Image();

    /**
     * Initialize Card.
     * 
     * @param {*} param Label properties.
     */
    function _init (param) {
        for (const attrName in param) {
            if (param.hasOwnProperty(attrName) && properties.hasOwnProperty(attrName)) {
                properties[attrName] = param[attrName];
            }
        }
        properties.original_canvas_x = param.canvas_x;
        properties.original_canvas_y = param.canvas_y;
        properties.val_counts = {
            'Agree': param.agree_count,
            'Disagree': param.disagree_count,
            'Unsure': param.unsure_count
        }
        if (properties.correct) properties.correctness = "correct";
        else if (properties.correct === false) properties.correctness = "incorrect";
        else if (param.agree_count + param.disagree_count + param.unsure_count > 0) properties.correctness = "unsure";
        else properties.correctness = "unvalidated";

        // Place label icon.
        labelIcon.src = iconImagePaths[getLabelType()];
        labelIcon.classList.add("label-icon", "label-icon-gallery");
        labelIcon.style.left = `calc(${100 * properties.original_canvas_x / (util.EXPLORE_CANVAS_WIDTH)}% - var(--iconWidth) / 2)`;
        labelIcon.style.top = `calc(${100 * properties.original_canvas_y / (util.EXPLORE_CANVAS_HEIGHT)}% - var(--iconWidth) / 2)`;

        // Create an element for the image in the card.
        imageId = "label_id_" + properties.label_id;
        panoImage.id = imageId;
        panoImage.className = "static-gallery-image";

        // Create the container card.
        card = document.createElement('div');
        card.id = "gallery_card_" + properties.label_id;
        card.className = "gallery-card";
        let imageHolder = document.createElement('div');
        imageHolder.className = "image-holder";
        card.appendChild(imageHolder);

        // Create the div for the severity and tags information.
        let cardInfo = document.createElement('div');
        cardInfo.className = 'card-info';

        // Create the div to store the label type.
        let cardHeader = document.createElement('div');
        cardHeader.className = 'card-header';
        cardHeader.innerHTML = `<div>${i18next.t(util.camelToKebab(getLabelType()))}</div>`;
        cardInfo.appendChild(cardHeader);

        // Create the div that will hold the severity and tags.
        let cardData = document.createElement('div');
        cardData.className = 'card-data';
        cardInfo.appendChild(cardData);

        // Create the div to store the severity of the label.
        let cardSeverity = document.createElement('div');
        cardSeverity.className = 'card-severity';
        new SeverityDisplay(cardSeverity, properties.severity, getLabelType());
        cardData.appendChild(cardSeverity);

        // Create the div to store the validation info of the label.
        let cardValidationInfo = document.createElement('div');
        cardValidationInfo.className = 'card-validation-info';
        self.validationInfoDisplay = new ValidationInfoDisplay(cardValidationInfo, properties.val_counts['Agree'], properties.val_counts['Disagree']);
        cardData.appendChild(cardValidationInfo);


        // Create the div to store the tags related to a card. Tags won't be populated until card is added to the DOM.
        let cardTags = document.createElement('div');
        cardTags.className = 'card-tags';
        cardTags.innerHTML = `<div class="label-tags-header"></div>`;
        cardTags.id = properties.label_id;
        cardData.appendChild(cardTags);

        // Append the overlays for label information on top of the image.
        imageHolder.appendChild(labelIcon);
        imageHolder.appendChild(panoImage);
        card.appendChild(cardInfo);
        validationMenu = new ValidationMenu(self, $(imageHolder), properties, modal, false);
    }

    /**
     * This function returns labelId property.
     * 
     * @returns {string}
     */
    function getLabelId () {
        return properties.label_id;
    }

    /**
     * This function returns labelType property.
     * 
     * @returns {string}
     */
    function getLabelType () {
        return properties.label_type;
    }

    /**
     * Return the deep copy of the properties object, so the caller can only modify properties from setProperty().
     * JavaScript Deepcopy:
     * http://stackoverflow.com/questions/122102/what-is-the-most-efficient-way-to-clone-a-javascript-object
     */
    function getProperties () { return $.extend(true, {}, properties); }

    /**
     * Get a property.
     * 
     * @param propName Property name.
     * @returns {*} Property value if property name is valid. Otherwise false.
     */
    function getProperty (propName) { return (propName in properties) ? properties[propName] : false; }

    /**
     * Get status of card.
     */
    function getStatus() {
        return status;
    }

    /**
     * Loads the pano image from url.
     */
    function loadImage() {
        return new Promise(resolve => {
            if (!status.imageFetched) {
                let img = panoImage;
                img.onload = () => {
                    status.imageFetched = true;
                    resolve(true);
                };
                img.src = imageUrl;
            } else {
                resolve(true);
            }
        });
    }

    /**
     * Renders the card.
     * TODO: should there be a safety check here to make sure pano is loaded?
     * 
     * @param cardContainer UI element to render card in.
     * @returns {self}
     */
    function render(cardContainer) {
        // If the card had transparent background from the modal being open earlier, remove transparency on rerender.
        if (card.classList.contains('modal-background-card')) card.classList.remove('modal-background-card');
        cardContainer.append(card);
        renderTags();
    }

    /**
     * Renders the tags on the card when the card is loaded onto on the DOM.
     */
    function renderTags() {
        let selector = ".card-tags#" + properties.label_id;
        let tagContent = new TagDisplay(selector, properties.tags);
    }

    /**
     * Sets a property. 
     * 
     * @param key Property name.
     * @param value Property value.
     * @returns {setProperty}
     */
    function setProperty (key, value) {
        properties[key] = value;
        return this;
    }

    /**
     * Set aspect of status.
     * 
     * @param {*} key Status name.
     * @param {*} value Status value.
     */
    function setStatus(key, value) {
        if (key in status) {
            status[key] = value;
        } else {
            throw self.className + ": Illegal status name.";
        }
    }

    /**
     * Updates metadata and visuals based on a new validation from the user.
     *
     * @param newUserValidation
     */
    function updateUserValidation(newUserValidation) {
        if (newUserValidation !== properties.user_validation) {
            // Update the metadata.
            properties.val_counts[properties.user_validation] -= 1;
            properties.val_counts[newUserValidation] += 1;
            properties.user_validation = newUserValidation;

            // Update the validation displays.
            self.validationInfoDisplay.updateValCounts(properties.val_counts['Agree'], properties.val_counts['Disagree']);
            self.validationMenu.showValidationOnCard(newUserValidation);

            // If this card matches the currently open modal, update the validation displays on the modal as well.
            if (modal.getProperty('label_id') === properties.label_id) {
                modal.validationInfoDisplay.updateValCounts(properties.val_counts['Agree'], properties.val_counts['Disagree']);
                modal.validationMenu.showValidationOnExpandedView(newUserValidation);
            }
        }
    }

    /**
     * Returns the current ImageID being displayed in the image.
     * @returns the image ID of the card that is being displayed
     */
    function getImageId() {
        return imageId
    }

    self.getLabelId = getLabelId;
    self.getLabelType = getLabelType;
    self.getProperties = getProperties;
    self.getProperty = getProperty;
    self.getStatus = getStatus;
    self.loadImage = loadImage;
    self.render = render;
    self.setProperty = setProperty;
    self.setStatus = setStatus;
    self.updateUserValidation = updateUserValidation;
    self.getImageId = getImageId;

    _init(params);
    
    self.validationMenu = validationMenu;

    return this;
}
